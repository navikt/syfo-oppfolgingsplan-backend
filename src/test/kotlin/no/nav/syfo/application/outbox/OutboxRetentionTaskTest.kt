package no.nav.syfo.application.outbox

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import no.nav.syfo.TestDB
import no.nav.syfo.application.database.exposedTransaction
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.application.outbox.db.findOutboxMessage
import no.nav.syfo.application.outbox.db.markOutboxMessageCancelled
import no.nav.syfo.application.outbox.db.markOutboxMessageSent
import no.nav.syfo.application.outbox.domain.OutboxCancellationReason
import no.nav.syfo.application.outbox.domain.OutboxStatus
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class OutboxRetentionTaskTest :
    DescribeSpec({
        val now = Instant.parse("2026-08-14T12:00:00Z")
        val completedAt = now.minus(Duration.ofDays(100))

        beforeTest { TestDB.clearAllData() }

        it("deletes terminal messages in batches and preserves active messages") {
            val terminalMessages = List(3) {
                TestDB.database.enqueueTestOutboxMessage(availableAt = completedAt)
            }
            val activeMessage = TestDB.database.enqueueTestOutboxMessage(availableAt = now)
            val claims = TestDB.database.claim(completedAt, limit = terminalMessages.size)
            TestDB.database.exposedTransaction {
                claims.take(2).forEach { message ->
                    markOutboxMessageSent(message.uuid, message.claimToken.shouldNotBeNull(), completedAt)
                }
                claims.last().let { message ->
                    markOutboxMessageCancelled(
                        message.uuid,
                        message.claimToken.shouldNotBeNull(),
                        OutboxCancellationReason.SOURCE_NO_LONGER_ELIGIBLE,
                        completedAt,
                    )
                }
            }
            val freshTerminalMessage = TestDB.database.enqueueTestOutboxMessage(
                availableAt = now.minus(Duration.ofDays(1)),
            )
            val freshClaim = TestDB.database.claim(now, limit = 1).single()
            TestDB.database.exposedTransaction {
                markOutboxMessageSent(
                    freshClaim.uuid,
                    freshClaim.claimToken.shouldNotBeNull(),
                    now.minus(Duration.ofDays(1)),
                )
            }

            OutboxRetentionTask(
                database = TestDB.database,
                leaderElection = mockk<LeaderElection>(),
                policies = listOf(
                    OutboxRetentionPolicy(
                        messageType = TEST_IMMEDIATE_MESSAGE,
                        retention = Duration.ofDays(90),
                    ),
                ),
                clock = Clock.fixed(now, ZoneOffset.UTC),
                batchSize = 1,
            ).execute()

            terminalMessages.forEach { message ->
                TestDB.database.findOutboxMessage(message).shouldBeNull()
            }
            TestDB.database.findOutboxMessage(activeMessage)?.status shouldBe OutboxStatus.READY
            TestDB.database.findOutboxMessage(freshTerminalMessage)?.status shouldBe OutboxStatus.SENT
        }
    })
