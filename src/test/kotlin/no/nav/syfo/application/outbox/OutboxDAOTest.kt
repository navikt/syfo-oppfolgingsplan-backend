package no.nav.syfo.application.outbox

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import no.nav.syfo.TestDB
import no.nav.syfo.application.database.exposedTransaction
import no.nav.syfo.application.outbox.db.claimOutboxMessages
import no.nav.syfo.application.outbox.db.deferOutboxMessage
import no.nav.syfo.application.outbox.db.deleteTerminalOutboxMessages
import no.nav.syfo.application.outbox.db.enqueueOutboxMessage
import no.nav.syfo.application.outbox.db.findOutboxMessage
import no.nav.syfo.application.outbox.db.markOutboxMessageCancelled
import no.nav.syfo.application.outbox.db.markOutboxMessageSent
import no.nav.syfo.application.outbox.db.readOutboxQueueSnapshot
import no.nav.syfo.application.outbox.db.recordOutboxMessageFailure
import no.nav.syfo.application.outbox.domain.NewOutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxCancellationReason
import no.nav.syfo.application.outbox.domain.OutboxStatus
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes

class OutboxDAOTest :
    DescribeSpec({
        val now = Instant.parse("2026-08-12T12:00:00Z")

        beforeTest { TestDB.clearAllData() }

        describe("enqueueOutboxMessage") {
            it("is idempotent for message type and dedup key") {
                val first = TestDB.database.enqueueTestOutboxMessage(dedupKey = "same-domain-command")

                val insertedAgain = TestDB.database.exposedTransaction {
                    enqueueOutboxMessage(
                        NewOutboxMessage(
                            messageType = TEST_IMMEDIATE_MESSAGE,
                            dedupKey = first.dedupKey,
                            externalRef = "another-reference",
                            payload = "{}",
                            availableAt = now,
                        ),
                    )
                }

                insertedAgain shouldBe false
                TestDB.database.findOutboxMessage(TEST_IMMEDIATE_MESSAGE, first.dedupKey) shouldBe first
            }

            it("keeps commands immutable and uses a new generation for a later opt-in") {
                val original = TestDB.database.enqueueTestOutboxMessage(dedupKey = "generation-1")
                val claim = TestDB.database.claim(now).single()
                TestDB.database.exposedTransaction {
                    markOutboxMessageCancelled(
                        claim.uuid,
                        claim.claimToken.shouldNotBeNull(),
                        OutboxCancellationReason.NO_LONGER_REQUESTED,
                        now,
                    )
                } shouldBe true

                val laterGeneration = TestDB.database.enqueueTestOutboxMessage(dedupKey = "generation-2")

                (laterGeneration.uuid == original.uuid) shouldBe false
                TestDB.database.findOutboxMessage(original)?.status shouldBe OutboxStatus.CANCELLED
                TestDB.database.findOutboxMessage(laterGeneration)?.status shouldBe OutboxStatus.READY
            }

            it("fails instead of silently ignoring a uuid collision for another command") {
                val sharedUuid = UUID.randomUUID()
                TestDB.database.enqueueTestOutboxMessage(uuid = sharedUuid, dedupKey = "first-command")

                shouldThrow<Exception> {
                    TestDB.database.exposedTransaction {
                        enqueueOutboxMessage(
                            NewOutboxMessage(
                                uuid = sharedUuid,
                                messageType = TEST_IMMEDIATE_MESSAGE,
                                dedupKey = "second-command",
                                externalRef = "another-reference",
                                payload = "{}",
                                availableAt = now,
                            ),
                        )
                    }
                }

                TestDB.database.findOutboxMessage(TEST_IMMEDIATE_MESSAGE, "second-command").shouldBeNull()
            }

            it("replays a concurrent idempotent domain transaction after serialization failure") {
                val firstInsertCompleted = CountDownLatch(1)
                val allowFirstCommit = CountDownLatch(1)
                val secondAttemptStarted = CountDownLatch(1)
                val secondAttempts = AtomicInteger(0)
                val dedupKey = "concurrent-command"

                coroutineScope {
                    val first = async(Dispatchers.IO) {
                        TestDB.database.exposedTransaction {
                            enqueueOutboxMessage(newMessage(dedupKey, "first-reference", now)).also {
                                firstInsertCompleted.countDown()
                                allowFirstCommit.await()
                            }
                        }
                    }
                    firstInsertCompleted.await()
                    val second = async(Dispatchers.IO) {
                        TestDB.database.exposedTransaction(maxAttempts = 3) {
                            secondAttempts.incrementAndGet()
                            secondAttemptStarted.countDown()
                            enqueueOutboxMessage(newMessage(dedupKey, "second-reference", now))
                        }
                    }
                    secondAttemptStarted.await()
                    delay(250)
                    allowFirstCommit.countDown()

                    first.await() shouldBe true
                    second.await() shouldBe false
                }

                secondAttempts.get() shouldBe 2
                TestDB.database.findOutboxMessage(TEST_IMMEDIATE_MESSAGE, dedupKey)?.externalRef shouldBe "first-reference"
            }

            it("rejects malformed JSON payloads in the database") {
                shouldThrow<Exception> {
                    TestDB.database.exposedTransaction {
                        enqueueOutboxMessage(
                            newMessage("invalid-json", "reference", now).copy(payload = "not-json"),
                        )
                    }
                }
            }
        }

        describe("claimOutboxMessages") {
            it("claims only due messages and persists a lease token") {
                val due = TestDB.database.enqueueTestOutboxMessage(availableAt = now.minusSeconds(1))
                TestDB.database.enqueueTestOutboxMessage(availableAt = now.plusSeconds(1))

                val claimed = TestDB.database.claim(now).single()

                claimed.uuid shouldBe due.uuid
                claimed.status shouldBe OutboxStatus.CLAIMED
                claimed.claimToken.shouldNotBeNull()
                claimed.leaseUntil shouldBe now.plusSeconds(5 * 60)
                TestDB.database.findOutboxMessage(due) shouldBe claimed
            }

            it("lets concurrent replicas claim disjoint rows without waiting") {
                val firstMessage = TestDB.database.enqueueTestOutboxMessage(availableAt = now.minusSeconds(2))
                val secondMessage = TestDB.database.enqueueTestOutboxMessage(availableAt = now.minusSeconds(1))
                val firstClaimed = CountDownLatch(1)
                val releaseFirstClaim = CountDownLatch(1)

                val claims = coroutineScope {
                    val first = async(Dispatchers.IO) {
                        TestDB.database.exposedTransaction {
                            claimOutboxMessages(TEST_IMMEDIATE_MESSAGE, now, 1, 5.minutes).also {
                                firstClaimed.countDown()
                                releaseFirstClaim.await()
                            }
                        }
                    }
                    firstClaimed.await()
                    val second = async(Dispatchers.IO) {
                        TestDB.database.claim(now, limit = 1)
                    }
                    val secondResult = second.await()
                    releaseFirstClaim.countDown()
                    first.await() + secondResult
                }

                claims.map { it.uuid } shouldContainExactlyInAnyOrder listOf(firstMessage.uuid, secondMessage.uuid)
                claims.map { it.claimToken }.distinct().size shouldBeExactly 2
            }

            it("reclaims an expired lease with a new token") {
                val firstClaim = TestDB.database.run {
                    enqueueTestOutboxMessage(availableAt = now)
                    claim(now).single()
                }

                TestDB.database.claim(now.plusSeconds(299)).shouldBeEmpty()
                val reclaimed = TestDB.database.claim(now.plusSeconds(300)).single()

                reclaimed.uuid shouldBe firstClaim.uuid
                (reclaimed.claimToken == firstClaim.claimToken) shouldBe false
                reclaimed.leaseUntil shouldBe now.plusSeconds(600)
            }

            it("uses uuid as a deterministic tie-breaker") {
                val lowerUuid = UUID.fromString("00000000-0000-0000-0000-000000000001")
                val higherUuid = UUID.fromString("00000000-0000-0000-0000-000000000002")
                TestDB.database.enqueueTestOutboxMessage(uuid = higherUuid, availableAt = now)
                TestDB.database.enqueueTestOutboxMessage(uuid = lowerUuid, availableAt = now)
                TestDB.database.exposedTransaction {
                    exec("UPDATE outbox SET created_at = '2026-08-12T11:00:00Z'")
                }

                TestDB.database.claim(now, limit = 1).single().uuid shouldBe lowerUuid
            }
        }

        describe("claim-guarded transitions") {
            it("prevents a stale claimant from overwriting a newer claim") {
                TestDB.database.enqueueTestOutboxMessage(availableAt = now)
                val staleClaim = TestDB.database.claim(now).single()
                val currentClaim = TestDB.database.claim(now.plusSeconds(300)).single()

                TestDB.database.exposedTransaction {
                    markOutboxMessageSent(staleClaim.uuid, staleClaim.claimToken.shouldNotBeNull(), now.plusSeconds(301))
                } shouldBe false
                TestDB.database.exposedTransaction {
                    markOutboxMessageSent(currentClaim.uuid, currentClaim.claimToken.shouldNotBeNull(), now.plusSeconds(302))
                } shouldBe true

                val persisted = TestDB.database.findOutboxMessage(currentClaim).shouldNotBeNull()
                persisted.status shouldBe OutboxStatus.SENT
                persisted.completedAt shouldBe now.plusSeconds(302)
                persisted.claimToken.shouldBeNull()
                persisted.leaseUntil.shouldBeNull()
            }

            it("returns deferred commands to READY without spending a technical failure") {
                TestDB.database.enqueueTestOutboxMessage(availableAt = now)
                val claim = TestDB.database.claim(now).single()
                val deferredUntil = now.plusSeconds(3600)

                TestDB.database.exposedTransaction {
                    deferOutboxMessage(claim.uuid, claim.claimToken.shouldNotBeNull(), deferredUntil)
                } shouldBe true

                val persisted = TestDB.database.findOutboxMessage(claim).shouldNotBeNull()
                persisted.status shouldBe OutboxStatus.READY
                persisted.availableAt shouldBe deferredUntil
                persisted.failureCount shouldBeExactly 0
                persisted.claimToken.shouldBeNull()
            }

            it("records a technical failure and schedules a retry") {
                TestDB.database.enqueueTestOutboxMessage(availableAt = now)
                val claim = TestDB.database.claim(now).single()
                val failedAt = now.plusSeconds(1)
                val retryAt = now.plusSeconds(61)

                TestDB.database.exposedTransaction {
                    recordOutboxMessageFailure(
                        claim.uuid,
                        claim.claimToken.shouldNotBeNull(),
                        failedAt,
                        retryAt,
                    )
                } shouldBe true

                val persisted = TestDB.database.findOutboxMessage(claim).shouldNotBeNull()
                persisted.status shouldBe OutboxStatus.READY
                persisted.failureCount shouldBeExactly 1
                persisted.lastFailureAt shouldBe failedAt
                persisted.availableAt shouldBe retryAt
            }

            it("starts a new technical failure sequence after a successful domain deferral") {
                TestDB.database.enqueueTestOutboxMessage(availableAt = now)
                val failedClaim = TestDB.database.claim(now).single()
                val retryAt = now.plusSeconds(60)
                TestDB.database.exposedTransaction {
                    recordOutboxMessageFailure(
                        failedClaim.uuid,
                        failedClaim.claimToken.shouldNotBeNull(),
                        failedAt = now,
                        retryAt = retryAt,
                    )
                }
                val retryClaim = TestDB.database.claim(retryAt).single()

                TestDB.database.exposedTransaction {
                    deferOutboxMessage(
                        retryClaim.uuid,
                        retryClaim.claimToken.shouldNotBeNull(),
                        until = retryAt.plusSeconds(3600),
                    )
                } shouldBe true

                val persisted = TestDB.database.findOutboxMessage(retryClaim).shouldNotBeNull()
                persisted.failureCount shouldBeExactly 0
                persisted.lastFailureAt.shouldBeNull()
                val snapshot = TestDB.database.exposedTransaction(readOnly = true) {
                    readOutboxQueueSnapshot(TEST_IMMEDIATE_MESSAGE, retryAt.plusSeconds(1))
                }
                snapshot.retryingCount shouldBe 0
                snapshot.maxFailureCount shouldBe 0
            }
        }

        describe("retention") {
            it("deletes terminal messages in bounded batches") {
                TestDB.database.enqueueTestOutboxMessage(dedupKey = "sent", availableAt = now)
                TestDB.database.enqueueTestOutboxMessage(dedupKey = "cancelled", availableAt = now)
                val claims = TestDB.database.claim(now)
                val completedAt = now.minus(Duration.ofDays(100))
                TestDB.database.exposedTransaction {
                    markOutboxMessageSent(claims[0].uuid, claims[0].claimToken.shouldNotBeNull(), completedAt)
                    markOutboxMessageCancelled(
                        claims[1].uuid,
                        claims[1].claimToken.shouldNotBeNull(),
                        OutboxCancellationReason.NO_LONGER_REQUESTED,
                        completedAt,
                    )
                }

                TestDB.database.exposedTransaction {
                    deleteTerminalOutboxMessages(
                        TEST_IMMEDIATE_MESSAGE,
                        completedBefore = now.minus(Duration.ofDays(90)),
                        batchSize = 1,
                    )
                } shouldBe 1
                TestDB.database.exposedTransaction {
                    deleteTerminalOutboxMessages(
                        TEST_IMMEDIATE_MESSAGE,
                        completedBefore = now.minus(Duration.ofDays(90)),
                        batchSize = 1,
                    )
                } shouldBe 1
            }
        }

        describe("queue observability") {
            it("reports unresolved failures while their retry is waiting in backoff") {
                TestDB.database.enqueueTestOutboxMessage(availableAt = now)
                val claim = TestDB.database.claim(now).single()
                TestDB.database.exposedTransaction {
                    recordOutboxMessageFailure(
                        claim.uuid,
                        claim.claimToken.shouldNotBeNull(),
                        failedAt = now,
                        retryAt = now.plusSeconds(3600),
                    )
                    exec("UPDATE outbox SET failure_count = 6 WHERE uuid = '${claim.uuid}'")
                }

                val snapshot = TestDB.database.exposedTransaction(readOnly = true) {
                    readOutboxQueueSnapshot(TEST_IMMEDIATE_MESSAGE, now.plusSeconds(1))
                }

                snapshot.dueReadyCount shouldBe 0
                snapshot.retryingCount shouldBe 1
                snapshot.maxFailureCount shouldBe 6
            }
        }

        describe("persisted types") {
            it("round-trips the stable message type") {
                val persisted = TestDB.database.enqueueTestOutboxMessage()
                persisted.messageType shouldBe TestOutboxMessageType.CREATED
            }

            it("rejects unknown cancellation reasons") {
                shouldThrow<IllegalStateException> {
                    OutboxCancellationReason.fromDatabaseValue("SOMETHING_UNBOUNDED")
                }
            }
        }
    })

private fun newMessage(dedupKey: String, externalRef: String, availableAt: Instant) = NewOutboxMessage(
    messageType = TEST_IMMEDIATE_MESSAGE,
    dedupKey = dedupKey,
    externalRef = externalRef,
    payload = "{}",
    availableAt = availableAt,
)
