package no.nav.syfo.application.outbox

import io.micrometer.core.instrument.Gauge
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY
import no.nav.syfo.application.outbox.db.OutboxQueueSnapshot
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal object OutboxQueueMetrics {
    private val observations = ConcurrentHashMap<String, Observation>()

    fun observe(
        messageType: OutboxMessageType,
        snapshot: OutboxQueueSnapshot,
        now: Instant,
    ) {
        observations.computeIfAbsent(messageType.value, ::register).apply {
            dueReadyCount.set(snapshot.dueReadyCount)
            expiredClaimCount.set(snapshot.expiredClaimCount)
            retryingCount.set(snapshot.retryingCount)
            maxFailureCount.set(snapshot.maxFailureCount.toLong())
            oldestDueAgeSeconds.set(
                snapshot.oldestDueAt
                    ?.let { Duration.between(it, now).seconds.coerceAtLeast(0) }
                    ?: 0,
            )
            // Publish freshness last, so a current timestamp never accompanies values from before
            // this successful database observation.
            lastSuccessTimestampSeconds.set(now.epochSecond)
        }
    }

    private fun register(messageType: String) = Observation().also { observation ->
        registerGauge("outbox_due_ready", "Number of due READY outbox messages", messageType, observation.dueReadyCount)
        registerGauge(
            "outbox_oldest_due_age_seconds",
            "Age in seconds of the oldest due READY outbox message",
            messageType,
            observation.oldestDueAgeSeconds,
        )
        registerGauge(
            "outbox_expired_claims",
            "Number of CLAIMED outbox messages with an expired lease",
            messageType,
            observation.expiredClaimCount,
        )
        registerGauge(
            "outbox_retrying",
            "Number of non-terminal outbox messages with technical failures",
            messageType,
            observation.retryingCount,
        )
        registerGauge(
            "outbox_max_failure_count",
            "Highest failure count among non-terminal outbox messages",
            messageType,
            observation.maxFailureCount,
        )
        registerGauge(
            "outbox_queue_snapshot_last_success_timestamp_seconds",
            "Unix timestamp of the last successful outbox queue snapshot",
            messageType,
            observation.lastSuccessTimestampSeconds,
        )
    }

    private fun registerGauge(
        suffix: String,
        description: String,
        messageType: String,
        value: AtomicLong,
    ) {
        Gauge.builder("${METRICS_NS}_$suffix", value) { it.get().toDouble() }
            .description(description)
            .tag("message_type", messageType)
            .register(METRICS_REGISTRY)
    }

    private class Observation(
        val dueReadyCount: AtomicLong = AtomicLong(),
        val oldestDueAgeSeconds: AtomicLong = AtomicLong(),
        val expiredClaimCount: AtomicLong = AtomicLong(),
        val retryingCount: AtomicLong = AtomicLong(),
        val maxFailureCount: AtomicLong = AtomicLong(),
        val lastSuccessTimestampSeconds: AtomicLong = AtomicLong(),
    )
}
