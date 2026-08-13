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
import no.nav.syfo.application.outbox.db.markOutboxMessageCancelled
import no.nav.syfo.application.outbox.db.markOutboxMessageSent
import no.nav.syfo.application.outbox.db.recordOutboxMessageFailure
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import no.nav.syfo.util.logger
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class OutboxBatchResult(
    val sent: Int = 0,
    val cancelled: Int = 0,
    val deferred: Int = 0,
    val retryScheduled: Int = 0,
) {
    val processed: Int
        get() = sent + cancelled + deferred + retryScheduled

    operator fun plus(other: OutboxBatchResult) = OutboxBatchResult(
        sent = sent + other.sent,
        cancelled = cancelled + other.cancelled,
        deferred = deferred + other.deferred,
        retryScheduled = retryScheduled + other.retryScheduled,
    )
}

class OutboxProcessor(
    private val database: DatabaseInterface,
    handlers: List<OutboxMessageHandler>,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = logger()
    private val handlersByType = handlers.associateBy(OutboxMessageHandler::messageType)

    init {
        require(handlers.isNotEmpty()) { "At least one outbox handler must be registered" }
        require(handlers.size == handlersByType.size) { "Only one outbox handler can be registered per message type" }
    }

    suspend fun processReadyMessages(batchSizePerType: Int = DEFAULT_BATCH_SIZE): OutboxBatchResult {
        require(batchSizePerType > 0) { "batchSizePerType must be greater than zero" }

        return handlersByType.values.fold(OutboxBatchResult()) { total, handler ->
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
            when (val attempt = processNextMessage(handler)) {
                ProcessAttempt.Empty -> return result
                is ProcessAttempt.Processed -> {
                    result += attempt.outcome.toBatchResult()
                    incrementMetric(handler.messageType, attempt.outcome.metricValue)
                }
                is ProcessAttempt.RetryScheduled -> {
                    result = result.copy(retryScheduled = result.retryScheduled + 1)
                    incrementMetric(handler.messageType, "retry_scheduled")
                    log.error(
                        "Failed to handle outbox message, outboxUuid={}, messageType={}, exceptionType={}",
                        attempt.messageUuid,
                        handler.messageType,
                        attempt.cause.javaClass.name,
                    )
                }
            }
        }

        return result
    }

    private suspend fun processNextMessage(handler: OutboxMessageHandler): ProcessAttempt = database.suspendedExposedTransaction {
        val startedAt = clock.instant()
        val message = claimNextReadyOutboxMessage(handler.messageType, startedAt)
            ?: return@suspendedExposedTransaction ProcessAttempt.Empty
        val handlerSavepoint = connection.setSavepoint(HANDLER_SAVEPOINT)

        val outcome = try {
            handler.handle(this, message, startedAt).validate(startedAt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            connection.rollback(handlerSavepoint)
            val failedAt = clock.instant()
            val retryAt = handler.retryPolicy.nextRetryAt(message, failedAt)
            check(retryAt.isAfter(failedAt)) { "Outbox retry policy must schedule a future retry" }
            recordOutboxMessageFailure(message.uuid, failedAt, retryAt)
            return@suspendedExposedTransaction ProcessAttempt.RetryScheduled(message.uuid, e)
        }

        when (outcome) {
            OutboxResult.Sent -> markOutboxMessageSent(message.uuid, clock.instant())
            is OutboxResult.Cancelled -> markOutboxMessageCancelled(message.uuid, outcome.reason)
            is OutboxResult.Deferred -> {
                connection.rollback(handlerSavepoint)
                deferOutboxMessage(message.uuid, outcome.until)
                return@suspendedExposedTransaction ProcessAttempt.Processed(outcome)
            }
        }
        connection.releaseSavepoint(handlerSavepoint)
        ProcessAttempt.Processed(outcome)
    }

    private fun incrementMetric(messageType: OutboxMessageType, result: String) {
        METRICS_REGISTRY.counter(
            "${METRICS_NS}_outbox_messages",
            "message_type",
            messageType.value,
            "result",
            result,
        ).increment()
    }

    private fun OutboxResult.validate(now: Instant): OutboxResult = apply {
        if (this is OutboxResult.Deferred) {
            require(until.isAfter(now)) { "Deferred outbox messages must be scheduled in the future" }
        }
    }

    private fun OutboxResult.toBatchResult(): OutboxBatchResult = when (this) {
        OutboxResult.Sent -> OutboxBatchResult(sent = 1)
        is OutboxResult.Cancelled -> OutboxBatchResult(cancelled = 1)
        is OutboxResult.Deferred -> OutboxBatchResult(deferred = 1)
    }

    private val OutboxResult.metricValue: String
        get() = when (this) {
            OutboxResult.Sent -> "sent"
            is OutboxResult.Cancelled -> "cancelled"
            is OutboxResult.Deferred -> "deferred"
        }

    private sealed interface ProcessAttempt {
        data object Empty : ProcessAttempt
        data class Processed(val outcome: OutboxResult) : ProcessAttempt
        data class RetryScheduled(val messageUuid: UUID, val cause: Exception) : ProcessAttempt
    }

    private companion object {
        const val DEFAULT_BATCH_SIZE = 100
        const val HANDLER_SAVEPOINT = "outbox_handler"
    }
}
