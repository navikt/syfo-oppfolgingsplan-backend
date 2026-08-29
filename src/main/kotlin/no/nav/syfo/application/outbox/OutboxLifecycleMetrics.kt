package no.nav.syfo.application.outbox

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.outbox.domain.OutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import java.time.Duration
import java.time.Instant

internal const val OUTBOX_ENQUEUED = "${METRICS_NS}_outbox_enqueued"
internal const val OUTBOX_TERMINAL = "${METRICS_NS}_outbox_terminal"
internal const val OUTBOX_CREATED_TO_TERMINAL_LATENCY = "${METRICS_NS}_outbox_created_to_terminal_latency"

/**
 * Lifecycle signals for explicitly selected outbox message types. Implementations must keep the
 * selection bounded and must not add identifiers as labels.
 */
interface OutboxLifecycleMetrics {
    fun recordEnqueued(
        messageType: OutboxMessageType,
        count: Int = 1,
    )

    fun recordTerminal(
        message: OutboxMessage,
        outcome: OutboxResult,
        completedAt: Instant,
    )
}

object NoOutboxLifecycleMetrics : OutboxLifecycleMetrics {
    override fun recordEnqueued(
        messageType: OutboxMessageType,
        count: Int,
    ) = Unit

    override fun recordTerminal(
        message: OutboxMessage,
        outcome: OutboxResult,
        completedAt: Instant,
    ) = Unit
}

/**
 * These are operational counters, not an accounting source: a process crash after commit and
 * before metric registration can under-count an enqueue or terminal transition. Database state
 * remains authoritative for reconciliation.
 *
 * The latency starts at the immutable outbox creation time and stops when the handler outcome has
 * been persisted. Only immediate message types whose creation represents eligibility may opt in.
 * For a Budstikka handler, `handler_acknowledged` means Kafka acknowledged the producer record; it
 * does not mean that the notification was processed by Budstikka or delivered to the user.
 */
internal class MicrometerOutboxLifecycleMetrics(
    private val registry: MeterRegistry,
    observedMessageTypes: Set<String>,
) : OutboxLifecycleMetrics {
    private val observedMessageTypes = observedMessageTypes.toSet()

    init {
        require(this.observedMessageTypes.isNotEmpty()) { "At least one outbox message type must be observed" }
        require(this.observedMessageTypes.none(String::isBlank)) { "Observed outbox message types must not be blank" }
    }

    override fun recordEnqueued(
        messageType: OutboxMessageType,
        count: Int,
    ) {
        if (messageType.value !in observedMessageTypes || count <= 0) return

        Counter
            .builder(OUTBOX_ENQUEUED)
            .description("Durable outbox commands committed by selected message type")
            .tag(MESSAGE_TYPE, messageType.value)
            .register(registry)
            .increment(count.toDouble())
    }

    override fun recordTerminal(
        message: OutboxMessage,
        outcome: OutboxResult,
        completedAt: Instant,
    ) {
        if (message.messageType.value !in observedMessageTypes) return
        val outcomeLabel = outcome.terminalMetricLabel ?: return
        val tags = arrayOf(
            MESSAGE_TYPE,
            message.messageType.value,
            OUTCOME,
            outcomeLabel,
        )

        Counter
            .builder(OUTBOX_TERMINAL)
            .description("Persisted terminal outbox outcomes by selected message type")
            .tags(*tags)
            .register(registry)
            .increment()

        val createdToTerminal = Duration.between(message.createdAt, completedAt).coerceAtLeast(Duration.ZERO)
        Timer
            .builder(OUTBOX_CREATED_TO_TERMINAL_LATENCY)
            .description("Time from durable outbox creation until its terminal outcome is persisted")
            .tags(*tags)
            .register(registry)
            .record(createdToTerminal)
    }

    private val OutboxResult.terminalMetricLabel: String?
        get() = when (this) {
            OutboxResult.Sent -> "handler_acknowledged"
            is OutboxResult.Cancelled -> "cancelled_${reason.value.lowercase()}"
            is OutboxResult.Deferred -> null
        }

    private companion object {
        const val MESSAGE_TYPE = "message_type"
        const val OUTCOME = "outcome"
    }
}
