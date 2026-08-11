package no.nav.syfo.application.outbox

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.suspendedExposedTransaction
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY
import no.nav.syfo.application.outbox.db.claimNextReadyOutboxMessage
import no.nav.syfo.application.outbox.db.deferOutboxMessage
import no.nav.syfo.application.outbox.db.markOutboxMessageIrrelevant
import no.nav.syfo.application.outbox.db.markOutboxMessageSent
import no.nav.syfo.application.outbox.db.recordOutboxMessageFailure
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import no.nav.syfo.util.logger
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

    suspend fun processReadyMessages(batchSizePerType: Int = DEFAULT_BATCH_SIZE): OutboxBatchResult {
        require(batchSizePerType > 0) { "batchSizePerType must be greater than zero" }
        reconcileMissingMessages()
        return handlers.values.fold(OutboxBatchResult()) { total, handler ->
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
            } catch (e: RecordedOutboxFailure) {
                incrementMetric(handler.messageType, "failed")
                log.error(
                    "Failed to process outbox message of type {}, exceptionType={}",
                    handler.messageType,
                    e.cause.javaClass.name,
                )
                result = result.copy(failed = result.failed + 1)
                return@repeat
            } catch (e: Exception) {
                incrementMetric(handler.messageType, "failed")
                log.error(
                    "Aborting outbox batch after transaction failure for message type {}, exceptionType={}",
                    handler.messageType,
                    e.javaClass.name,
                )
                return result.copy(failed = result.failed + 1)
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

    private suspend fun processNextMessage(handler: OutboxMessageHandler): OutboxResult? {
        val attempt = database.suspendedExposedTransaction {
            val message = claimNextReadyOutboxMessage(handler.messageType, clock.instant())
                ?: return@suspendedExposedTransaction ProcessAttempt.Empty
            val handlerSavepoint = connection.setSavepoint(HANDLER_SAVEPOINT)

            try {
                when (val outcome = handler.process(this, message)) {
                    OutboxResult.SENT -> {
                        markOutboxMessageSent(message.uuid, clock.instant())
                        connection.releaseSavepoint(handlerSavepoint)
                        ProcessAttempt.Processed(outcome)
                    }
                    OutboxResult.IRRELEVANT -> {
                        markOutboxMessageIrrelevant(message.uuid)
                        connection.releaseSavepoint(handlerSavepoint)
                        ProcessAttempt.Processed(outcome)
                    }
                    OutboxResult.DEFERRED -> {
                        connection.rollback(handlerSavepoint)
                        deferOutboxMessage(message.uuid, clock.instant().plusSeconds(DEFER_SECONDS))
                        ProcessAttempt.Processed(outcome)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                connection.rollback(handlerSavepoint)
                val attemptedAt = clock.instant()
                recordOutboxMessageFailure(
                    uuid = message.uuid,
                    attemptedAt = attemptedAt,
                    retryAt = attemptedAt.plusSeconds(retryDelaySeconds(message.attemptCount)),
                    permanentlyFailed = message.attemptCount + 1 >= this@OutboxProcessor.maxAttempts,
                )
                ProcessAttempt.Failed(e)
            }
        }

        return when (attempt) {
            ProcessAttempt.Empty -> null
            is ProcessAttempt.Processed -> attempt.outcome
            is ProcessAttempt.Failed -> throw RecordedOutboxFailure(attempt.cause)
        }
    }

    private suspend fun reconcileMissingMessages() {
        reconcilers.forEach { reconciler ->
            val reconciled = database.suspendedExposedTransaction {
                reconciler.reconcile(this)
            }
            if (reconciled > 0) {
                log.info("Reconciled {} missing outbox messages", reconciled)
            }
        }
    }

    private fun retryDelaySeconds(attemptCount: Int): Long = minOf(
        1L shl attemptCount.coerceAtMost(MAX_BACKOFF_EXPONENT),
        MAX_RETRY_DELAY_MINUTES,
    ) * 60

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

    private sealed interface ProcessAttempt {
        data object Empty : ProcessAttempt
        data class Processed(val outcome: OutboxResult) : ProcessAttempt
        data class Failed(val cause: Exception) : ProcessAttempt
    }

    private class RecordedOutboxFailure(
        override val cause: Exception,
    ) : RuntimeException(cause)

    companion object {
        const val DEFAULT_BATCH_SIZE = 100
        const val DEFAULT_MAX_ATTEMPTS = 10
        private const val HANDLER_SAVEPOINT = "outbox_handler"
        private const val DEFER_SECONDS = 60L
        private const val MAX_BACKOFF_EXPONENT = 6
        private const val MAX_RETRY_DELAY_MINUTES = 60L
    }
}
