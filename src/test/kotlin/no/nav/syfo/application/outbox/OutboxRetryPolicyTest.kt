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
            )

            policy.nextRetryAt(message(attemptCount = 0), failedAt) shouldBe failedAt.plusSeconds(60)
            policy.nextRetryAt(message(attemptCount = 3), failedAt) shouldBe failedAt.plusSeconds(8 * 60)
            policy.nextRetryAt(message(attemptCount = 20), failedAt) shouldBe failedAt.plusSeconds(10 * 60)
        }
    })

private fun message(attemptCount: Int) = OutboxMessage(
    uuid = UUID.randomUUID(),
    messageType = TEST_IMMEDIATE_MESSAGE,
    dedupKey = "dedup-key",
    externalRef = "external-ref",
    payload = "{}",
    scheduledAt = Instant.EPOCH,
    status = OutboxStatus.READY,
    attemptCount = attemptCount,
    lastAttemptAt = null,
    createdAt = Instant.EPOCH,
    sentAt = null,
)
