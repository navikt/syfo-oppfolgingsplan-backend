package no.nav.syfo.application.outbox

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.exposedTransaction
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY
import no.nav.syfo.application.outbox.db.claimOutboxMessages
import no.nav.syfo.application.outbox.db.deferOutboxMessage
import no.nav.syfo.application.outbox.db.markOutboxMessageCancelled
import no.nav.syfo.application.outbox.db.markOutboxMessageSent
import no.nav.syfo.application.outbox.db.recordOutboxMessageFailure
import no.nav.syfo.application.outbox.domain.OutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import no.nav.syfo.util.logger
import java.time.Clock
import java.time.Duration.between
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class OutboxBatchResult(
    val sent: Int = 0,
    val cancelled: Int = 0,
    val deferred: Int = 0,
    val retryScheduled: Int = 0,
    val claimLost: Int = 0,
) {
    val processed: Int
        get() = sent + cancelled + deferred + retryScheduled + claimLost

    operator fun plus(other: OutboxBatchResult) = OutboxBatchResult(
        sent = sent + other.sent,
        cancelled = cancelled + other.cancelled,
        deferred = deferred + other.deferred,
        retryScheduled = retryScheduled + other.retryScheduled,
        claimLost = claimLost + other.claimLost,
    )
}

data class OutboxWorkerConfig(
    val batchSizePerType: Int = 25,
    val leaseDuration: Duration = 5.minutes,
    val leaseBudgetFraction: Double = 0.8,
    val maxConsecutiveFailures: Int = 3,
) {
    init {
        require(batchSizePerType > 0) { "batchSizePerType must be greater than zero" }
        require(leaseDuration.isFinite()) { "leaseDuration must be finite" }
        require(leaseDuration.isPositive()) { "leaseDuration must be positive" }
        require(leaseBudgetFraction > 0.0 && leaseBudgetFraction <= 1.0) {
            "leaseBudgetFraction must be in (0.0, 1.0]"
        }
        require(maxConsecutiveFailures > 0) { "maxConsecutiveFailures must be greater than zero" }
        require(leaseBudgetMillis > 0) { "lease budget must be at least one millisecond" }
    }

    internal val leaseBudgetMillis: Long
        get() = (leaseDuration.inWholeMilliseconds * leaseBudgetFraction).toLong()
}

/**
 * Claims batches in short transactions and handles them without holding a database connection.
 * PostgreSQL coordinates replicas; leases recover rows after cancellation, crashes, and timeouts.
 */
class OutboxWorker(
    private val database: DatabaseInterface,
    handlers: List<OutboxMessageHandler>,
    private val clock: Clock = Clock.systemUTC(),
    private val config: OutboxWorkerConfig = OutboxWorkerConfig(),
) {
    private val log = logger()
    private val handlersByTypeValue = handlers.associateBy { it.messageType.value }

    init {
        require(handlers.isNotEmpty()) { "At least one outbox handler must be registered" }
        require(handlers.all { it.messageType.value.isNotBlank() }) { "Outbox message type values must not be blank" }
        require(handlers.size == handlersByTypeValue.size) {
            "Only one outbox handler can be registered per stable message type value"
        }
    }

    suspend fun runOnce(): OutboxBatchResult = handlersByTypeValue.values.fold(OutboxBatchResult()) { total, handler ->
        total + processClaimedBatch(handler)
    }

    private suspend fun processClaimedBatch(handler: OutboxMessageHandler): OutboxBatchResult {
        val batchStartedAt = clock.instant()
        val claimed = database.exposedTransaction {
            claimOutboxMessages(
                messageType = handler.messageType,
                now = batchStartedAt,
                limit = config.batchSizePerType,
                leaseDuration = config.leaseDuration,
            )
        }
        if (claimed.isEmpty()) return OutboxBatchResult()

        var result = OutboxBatchResult()
        var consecutiveFailures = 0
        for ((index, message) in claimed.withIndex()) {
            currentCoroutineContext().ensureActive()
            if (between(batchStartedAt, clock.instant()).toMillis() >= config.leaseBudgetMillis) {
                log.warn(
                    "Stopping outbox batch because lease budget is spent, messageType={}, unprocessedCount={}, claimedCount={}",
                    handler.messageType,
                    claimed.size - index,
                    claimed.size,
                )
                break
            }

            val attempt = try {
                processClaimedMessage(handler, message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                consecutiveFailures++
                incrementMetric(handler.messageType, "processing_failed")
                log.error(
                    "Failed to process claimed outbox message, outboxUuid={}, messageType={}, exceptionType={}",
                    message.uuid,
                    handler.messageType,
                    e.javaClass.name,
                )
                if (consecutiveFailures >= config.maxConsecutiveFailures) {
                    logBatchAbort(handler.messageType, consecutiveFailures)
                    break
                }
                continue
            }

            when (attempt) {
                is ProcessAttempt.Processed -> {
                    consecutiveFailures = 0
                    result += attempt.outcome.toBatchResult()
                    incrementMetric(handler.messageType, attempt.outcome.metricValue)
                }
                is ProcessAttempt.RetryScheduled -> {
                    consecutiveFailures++
                    result = result.copy(retryScheduled = result.retryScheduled + 1)
                    incrementMetric(handler.messageType, "retry_scheduled")
                    log.error(
                        "Failed to handle outbox message, outboxUuid={}, messageType={}, exceptionType={}",
                        message.uuid,
                        handler.messageType,
                        attempt.cause.javaClass.name,
                    )
                }
                ProcessAttempt.ClaimLost -> {
                    consecutiveFailures++
                    result = result.copy(claimLost = result.claimLost + 1)
                    incrementMetric(handler.messageType, "claim_lost")
                    log.warn(
                        "Ignored stale outbox completion because the claim was lost, outboxUuid={}, messageType={}",
                        message.uuid,
                        handler.messageType,
                    )
                }
            }

            if (consecutiveFailures >= config.maxConsecutiveFailures) {
                logBatchAbort(handler.messageType, consecutiveFailures)
                break
            }
        }
        return result
    }

    private suspend fun processClaimedMessage(
        handler: OutboxMessageHandler,
        message: OutboxMessage,
    ): ProcessAttempt {
        val claimToken = requireNotNull(message.claimToken)
        val startedAt = clock.instant()
        val outcome = try {
            handler.handle(message, startedAt).validate(startedAt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val failedAt = clock.instant()
            val retryAt = handler.retryPolicy.nextRetryAt(message, failedAt)
            check(retryAt.isAfter(failedAt)) { "Outbox retry policy must schedule a future retry" }
            val recorded = database.exposedTransaction {
                recordOutboxMessageFailure(message.uuid, claimToken, failedAt, retryAt)
            }
            return if (recorded) ProcessAttempt.RetryScheduled(e) else ProcessAttempt.ClaimLost
        }

        val transitioned = database.exposedTransaction {
            when (outcome) {
                OutboxResult.Sent -> markOutboxMessageSent(message.uuid, claimToken, clock.instant())
                is OutboxResult.Cancelled -> markOutboxMessageCancelled(message.uuid, claimToken, outcome.reason)
                is OutboxResult.Deferred -> deferOutboxMessage(message.uuid, claimToken, outcome.until)
            }
        }
        return if (transitioned) ProcessAttempt.Processed(outcome) else ProcessAttempt.ClaimLost
    }

    private fun logBatchAbort(messageType: OutboxMessageType, consecutiveFailures: Int) {
        log.error(
            "Aborting outbox batch after {} consecutive failures for messageType={}",
            consecutiveFailures,
            messageType,
        )
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
        data class Processed(val outcome: OutboxResult) : ProcessAttempt
        data class RetryScheduled(val cause: Exception) : ProcessAttempt
        data object ClaimLost : ProcessAttempt
    }
}
