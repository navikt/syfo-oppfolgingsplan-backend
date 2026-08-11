package no.nav.syfo.application.outbox

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY
import no.nav.syfo.application.outbox.db.claimNextReadyOutboxMessage
import no.nav.syfo.application.outbox.db.deferOutboxMessage
import no.nav.syfo.application.outbox.db.markOutboxMessageIrrelevant
import no.nav.syfo.application.outbox.db.markOutboxMessageSent
import no.nav.syfo.application.outbox.db.recordOutboxMessageFailure
import no.nav.syfo.application.outbox.domain.OutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import no.nav.syfo.util.logger
import java.sql.Savepoint
import java.time.Clock

data class OutboxBatchResult(
    val sent: Int = 0,
    val irrelevant: Int = 0,
    val deferred: Int = 0,
    val failed: Int = 0,
) {
    val processed: Int
        get() = sent + irrelevant + deferred + failed

    operator fun plus(other: OutboxBatchResult): OutboxBatchResult = OutboxBatchResult(
        sent = sent + other.sent,
        irrelevant = irrelevant + other.irrelevant,
        deferred = deferred + other.deferred,
        failed = failed + other.failed,
    )
}

class OutboxProcessor(
    private val database: DatabaseInterface,
    handlers: List<OutboxMessageHandler>,
    private val reconcilers: List<OutboxMessageReconciler> = emptyList(),
    private val clock: Clock = Clock.systemUTC(),
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
) {
    private val log = logger()
    private val handlers = handlers.associateBy { it.messageType }

    init {
        require(handlers.size == this.handlers.size) { "Only one outbox handler can be registered per message type" }
        require(maxAttempts > 0) { "maxAttempts must be greater than zero" }
    }

    suspend fun processReadyMessages(batchSizePerType: Int = DEFAULT_BATCH_SIZE): OutboxBatchResult = withContext(Dispatchers.IO) {
        require(batchSizePerType > 0) { "batchSizePerType must be greater than zero" }
        reconcileMissingMessages()
        handlers.values.fold(OutboxBatchResult()) { total, handler ->
            total + processReadyMessages(handler, batchSizePerType)
        }
    }

    private suspend fun processReadyMessages(
        handler: OutboxMessageHandler,
        batchSize: Int,
    ): OutboxBatchResult {
        var result = OutboxBatchResult()

        repeat(batchSize) {
            currentCoroutineContext().ensureActive()
            val outcome = try {
                processNextMessage(handler) ?: return result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                incrementMetric(handler.messageType, "failed")
                log.error(
                    "Failed to process outbox message of type {}, exceptionType={}",
                    handler.messageType,
                    e.javaClass.name,
                )
                result = result.copy(failed = result.failed + 1)
                return@repeat
            }

            result = when (outcome) {
                OutboxResult.SENT -> result.copy(sent = result.sent + 1)
                OutboxResult.IRRELEVANT -> result.copy(irrelevant = result.irrelevant + 1)
                OutboxResult.DEFERRED -> return result.copy(deferred = result.deferred + 1)
            }
            incrementMetric(handler.messageType, outcome.name.lowercase())
        }

        return result
    }

    private suspend fun processNextMessage(handler: OutboxMessageHandler): OutboxResult? = database.connection.use { connection ->
        var claimedMessage: OutboxMessage? = null
        var handlerSavepoint: Savepoint? = null
        try {
            val message = connection.claimNextReadyOutboxMessage(handler.messageType, clock.instant())
                ?: return@use null
            claimedMessage = message
            handlerSavepoint = connection.setSavepoint()
            val outcome = handler.process(connection, message)
            when (outcome) {
                OutboxResult.SENT -> connection.markOutboxMessageSent(message.uuid, clock.instant())
                OutboxResult.IRRELEVANT -> connection.markOutboxMessageIrrelevant(message.uuid)
                OutboxResult.DEFERRED -> {
                    connection.rollback(handlerSavepoint)
                    connection.deferOutboxMessage(message.uuid, clock.instant().plusSeconds(DEFER_SECONDS))
                    connection.commit()
                    return@use outcome
                }
            }
            connection.commit()
            outcome
        } catch (e: CancellationException) {
            connection.rollback()
            throw e
        } catch (e: Exception) {
            if (claimedMessage != null && handlerSavepoint != null) {
                connection.rollback(handlerSavepoint)
                val attemptedAt = clock.instant()
                connection.recordOutboxMessageFailure(
                    uuid = claimedMessage.uuid,
                    attemptedAt = attemptedAt,
                    retryAt = attemptedAt.plusSeconds(retryDelaySeconds(claimedMessage.attemptCount)),
                    maxAttempts = maxAttempts,
                )
                connection.commit()
            } else {
                connection.rollback()
            }
            throw e
        }
    }

    private fun reconcileMissingMessages() {
        reconcilers.forEach { reconciler ->
            database.connection.use { connection ->
                try {
                    val reconciled = reconciler.reconcile(connection)
                    connection.commit()
                    if (reconciled > 0) {
                        log.info("Reconciled {} missing outbox messages", reconciled)
                    }
                } catch (e: Exception) {
                    connection.rollback()
                    throw e
                }
            }
        }
    }

    private fun retryDelaySeconds(attemptCount: Int): Long = minOf(1L shl attemptCount.coerceAtMost(MAX_BACKOFF_EXPONENT), MAX_RETRY_DELAY_MINUTES) * 60

    private fun incrementMetric(
        messageType: OutboxMessageType,
        result: String,
    ) {
        METRICS_REGISTRY.counter(
            "${METRICS_NS}_outbox_messages",
            "message_type",
            messageType.name,
            "result",
            result,
        ).increment()
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 100
        const val DEFAULT_MAX_ATTEMPTS = 10
        private const val DEFER_SECONDS = 60L
        private const val MAX_BACKOFF_EXPONENT = 6
        private const val MAX_RETRY_DELAY_MINUTES = 60L
    }
}
