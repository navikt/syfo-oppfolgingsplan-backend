package no.nav.syfo.application.outbox

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.longs.shouldBeExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.syfo.application.outbox.domain.OutboxCancellationReason
import no.nav.syfo.application.outbox.domain.OutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxStatus
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

class OutboxLifecycleMetricsTest :
    FunSpec({
        val createdAt = Instant.parse("2026-08-29T08:00:00Z")

        test("records committed commands only for the selected bounded message type") {
            val registry = SimpleMeterRegistry()
            val metrics = metrics(registry)

            metrics.recordEnqueued(TEST_IMMEDIATE_MESSAGE, count = 2)
            metrics.recordEnqueued(TEST_STAGED_MESSAGE)

            registry
                .get(OUTBOX_ENQUEUED)
                .tag("message_type", TEST_IMMEDIATE_MESSAGE.value)
                .counter()
                .count() shouldBeExactly 2.0
            registry.find(OUTBOX_ENQUEUED).tag("message_type", TEST_STAGED_MESSAGE.value).counter().shouldBeNull()
        }

        test("records acknowledged terminal outcome and full producer-leg latency across retry") {
            val registry = SimpleMeterRegistry()
            val metrics = metrics(registry)
            val message = claimedMessage(createdAt).copy(availableAt = createdAt.plusSeconds(3600))

            metrics.recordTerminal(message, OutboxResult.Sent, createdAt.plusSeconds(3612))

            registry
                .get(OUTBOX_TERMINAL)
                .tags(
                    "message_type",
                    TEST_IMMEDIATE_MESSAGE.value,
                    "outcome",
                    "handler_acknowledged",
                ).counter()
                .count() shouldBeExactly 1.0
            registry
                .get(OUTBOX_CREATED_TO_TERMINAL_LATENCY)
                .tags(
                    "message_type",
                    TEST_IMMEDIATE_MESSAGE.value,
                    "outcome",
                    "handler_acknowledged",
                ).timer()
                .apply {
                    count() shouldBeExactly 1L
                    totalTime(TimeUnit.SECONDS) shouldBeExactly 3612.0
                }
        }

        test("keeps expected cancellation reason separate and ignores deferral") {
            val registry = SimpleMeterRegistry()
            val metrics = metrics(registry)
            val message = claimedMessage(createdAt)

            metrics.recordTerminal(
                message,
                OutboxResult.Cancelled(OutboxCancellationReason.SOURCE_NO_LONGER_ELIGIBLE),
                createdAt.plusSeconds(3),
            )
            metrics.recordTerminal(message, OutboxResult.Deferred(createdAt.plusSeconds(60)), createdAt)

            registry
                .get(OUTBOX_TERMINAL)
                .tags(
                    "message_type",
                    TEST_IMMEDIATE_MESSAGE.value,
                    "outcome",
                    "cancelled_source_no_longer_eligible",
                ).counter()
                .count() shouldBeExactly 1.0
            registry.find(OUTBOX_TERMINAL).tag("outcome", "deferred").counter().shouldBeNull()
        }
    })

private fun metrics(registry: SimpleMeterRegistry) = MicrometerOutboxLifecycleMetrics(
    registry = registry,
    observedMessageTypes = setOf(TEST_IMMEDIATE_MESSAGE.value),
)

private fun claimedMessage(createdAt: Instant) = OutboxMessage(
    uuid = UUID.randomUUID(),
    messageType = TEST_IMMEDIATE_MESSAGE,
    dedupKey = "dedup-key",
    externalRef = "external-ref",
    payload = "{}",
    availableAt = createdAt,
    status = OutboxStatus.CLAIMED,
    failureCount = 0,
    lastFailureAt = null,
    createdAt = createdAt,
    completedAt = null,
    claimToken = UUID.randomUUID(),
    leaseUntil = createdAt.plusSeconds(300),
)
