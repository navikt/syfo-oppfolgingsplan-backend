package no.nav.syfo.application.outbox

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.syfo.application.outbox.domain.OutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxStatus
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

class OutboxRetryPolicyTest :
    DescribeSpec({
        val failedAt = Instant.parse("2026-08-12T12:00:00Z")

        it("uses exponential delay capped at the configured maximum") {
            val policy = ExponentialOutboxRetryPolicy(
                initialDelay = 1.minutes,
                maximumDelay = 10.minutes,
                jitterRatio = 0.0,
            )

            policy.nextRetryAt(message(failureCount = 0), failedAt) shouldBe failedAt.plusSeconds(60)
            policy.nextRetryAt(message(failureCount = 3), failedAt) shouldBe failedAt.plusSeconds(8 * 60)
            policy.nextRetryAt(message(failureCount = 20), failedAt) shouldBe failedAt.plusSeconds(10 * 60)
        }

        it("adds bounded jitter without exceeding the capped delay") {
            val policy = ExponentialOutboxRetryPolicy(
                initialDelay = 10.minutes,
                maximumDelay = 10.minutes,
                jitterRatio = 0.2,
                randomDouble = { 0.5 },
            )

            policy.nextRetryAt(message(failureCount = 0), failedAt) shouldBe failedAt.plusSeconds(9 * 60)
        }
    })

private fun message(failureCount: Int) = OutboxMessage(
    uuid = UUID.randomUUID(),
    messageType = TEST_IMMEDIATE_MESSAGE,
    dedupKey = "dedup-key",
    externalRef = "external-ref",
    payload = "{}",
    availableAt = Instant.EPOCH,
    status = OutboxStatus.READY,
    failureCount = failureCount,
    lastFailureAt = if (failureCount == 0) null else Instant.EPOCH,
    createdAt = Instant.EPOCH,
    completedAt = null,
)
