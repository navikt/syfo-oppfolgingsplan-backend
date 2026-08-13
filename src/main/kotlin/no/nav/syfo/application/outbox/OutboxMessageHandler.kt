package no.nav.syfo.application.outbox

import no.nav.syfo.application.outbox.domain.OutboxCancellationReason
import no.nav.syfo.application.outbox.domain.OutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Delivery and domain evaluation for exactly one outbox message type. */
interface OutboxMessageHandler {
    val messageType: OutboxMessageType

    /** Retry of technical failures is deliberately separate from domain-driven deferral. */
    val retryPolicy: OutboxRetryPolicy
        get() = ExponentialOutboxRetryPolicy()

    /**
     * Runs while the outbox row is locked. Domain reads may use [transaction] so the decision and
     * the terminal outbox status are committed atomically.
     */
    suspend fun handle(
        transaction: JdbcTransaction,
        message: OutboxMessage,
        now: Instant,
    ): OutboxResult
}

sealed interface OutboxResult {
    data object Sent : OutboxResult

    data class Cancelled(val reason: OutboxCancellationReason) : OutboxResult

    /** The domain is not ready yet. This does not count as a failed delivery attempt. */
    data class Deferred(val until: Instant) : OutboxResult
}

fun interface OutboxRetryPolicy {
    fun nextRetryAt(message: OutboxMessage, failedAt: Instant): Instant
}

class ExponentialOutboxRetryPolicy(
    private val initialDelay: Duration = 1.minutes,
    private val maximumDelay: Duration = 1.hours,
) : OutboxRetryPolicy {
    init {
        require(initialDelay.inWholeSeconds >= 1) { "initialDelay must be at least one second" }
        require(initialDelay.isFinite()) { "initialDelay must be finite" }
        require(maximumDelay.isFinite()) { "maximumDelay must be finite" }
        require(maximumDelay >= initialDelay) { "maximumDelay must be at least initialDelay" }
    }

    override fun nextRetryAt(message: OutboxMessage, failedAt: Instant): Instant {
        val multiplier = 1 shl message.attemptCount.coerceAtMost(MAX_EXPONENT)
        val delay = (initialDelay * multiplier).coerceAtMost(maximumDelay)
        return failedAt.plusMillis(delay.inWholeMilliseconds)
    }

    private companion object {
        const val MAX_EXPONENT = 30
    }
}
