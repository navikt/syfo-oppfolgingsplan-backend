package no.nav.syfo.application.outbox

import no.nav.syfo.application.outbox.domain.OutboxCancellationReason
import no.nav.syfo.application.outbox.domain.OutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import java.time.Instant
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Domain evaluation and delivery for exactly one outbox message type. */
interface OutboxMessageHandler {
    val messageType: OutboxMessageType

    /** Retry of technical failures is deliberately separate from domain-driven deferral. */
    val retryPolicy: OutboxRetryPolicy
        get() = ExponentialOutboxRetryPolicy()

    /**
     * Runs after the claim transaction has committed. Implementations must use bounded external
     * calls and idempotent side effects; the stable [OutboxMessage.uuid] is the deduplication ID.
     */
    suspend fun handle(
        message: OutboxMessage,
        now: Instant,
    ): OutboxResult
}

sealed interface OutboxResult {
    data object Sent : OutboxResult

    data class Cancelled(val reason: OutboxCancellationReason) : OutboxResult

    /** The domain is not ready yet. This does not count as a technical failure. */
    data class Deferred(val until: Instant) : OutboxResult
}

fun interface OutboxRetryPolicy {
    fun nextRetryAt(message: OutboxMessage, failedAt: Instant): Instant
}

class ExponentialOutboxRetryPolicy(
    private val initialDelay: Duration = 1.minutes,
    private val maximumDelay: Duration = 1.hours,
    private val jitterRatio: Double = 0.2,
    private val randomDouble: () -> Double = { Random.nextDouble() },
) : OutboxRetryPolicy {
    init {
        require(initialDelay.inWholeSeconds >= 1) { "initialDelay must be at least one second" }
        require(initialDelay.isFinite()) { "initialDelay must be finite" }
        require(maximumDelay.isFinite()) { "maximumDelay must be finite" }
        require(maximumDelay >= initialDelay) { "maximumDelay must be at least initialDelay" }
        require(jitterRatio >= 0.0 && jitterRatio < 1.0) { "jitterRatio must be in [0.0, 1.0)" }
    }

    override fun nextRetryAt(message: OutboxMessage, failedAt: Instant): Instant {
        val multiplier = 1 shl message.failureCount.coerceAtMost(MAX_EXPONENT)
        val cappedDelay = (initialDelay * multiplier).coerceAtMost(maximumDelay)
        val jitterMultiplier = 1.0 - (jitterRatio * randomDouble().coerceIn(0.0, 1.0))
        return failedAt.plusMillis((cappedDelay * jitterMultiplier).inWholeMilliseconds)
    }

    private companion object {
        const val MAX_EXPONENT = 30
    }
}
