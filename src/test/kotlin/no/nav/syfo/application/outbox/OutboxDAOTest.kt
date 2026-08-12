package no.nav.syfo.application.outbox

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import no.nav.syfo.TestDB
import no.nav.syfo.application.database.exposedTransaction
import no.nav.syfo.application.outbox.db.claimNextReadyOutboxMessage
import no.nav.syfo.application.outbox.db.enqueueOutboxMessage
import no.nav.syfo.application.outbox.db.findOutboxMessage
import no.nav.syfo.application.outbox.db.markOutboxMessageIrrelevant
import no.nav.syfo.application.outbox.db.markOutboxMessageSent
import no.nav.syfo.application.outbox.db.reactivateIrrelevantOutboxMessage
import no.nav.syfo.application.outbox.domain.NewOutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxStatus
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class OutboxDAOTest :
    DescribeSpec({
        beforeTest { TestDB.clearAllData() }

        describe("enqueueOutboxMessage") {
            it("is idempotent for message type and dedup key") {
                val first = TestDB.database.enqueueTestOutboxMessage(dedupKey = "same-domain-command")
                val insertedAgain = TestDB.database.exposedTransaction {
                    enqueueOutboxMessage(
                        NewOutboxMessage(
                            messageType = TEST_IMMEDIATE_MESSAGE,
                            dedupKey = "same-domain-command",
                            externalRef = "another-reference",
                            payload = "{}",
                            scheduledAt = Instant.now(),
                        ),
                    )
                }

                insertedAgain shouldBe false
                TestDB.database.exposedTransaction {
                    findOutboxMessage(TEST_IMMEDIATE_MESSAGE, "same-domain-command")
                } shouldBe first
            }

            it("does not revive a terminal message") {
                val first = TestDB.database.enqueueTestOutboxMessage(dedupKey = "terminal-command")
                TestDB.database.exposedTransaction {
                    markOutboxMessageIrrelevant(first.uuid)
                }

                val duplicate = TestDB.database.enqueueTestOutboxMessage(dedupKey = "terminal-command")

                duplicate.uuid shouldBe first.uuid
                duplicate.status shouldBe OutboxStatus.IRRELEVANT
            }

            it("explicitly reactivates an irrelevant scheduled message") {
                val original = TestDB.database.enqueueTestOutboxMessage(
                    messageType = TEST_SCHEDULED_MESSAGE,
                    dedupKey = "reminder-opt-in",
                    externalRef = "old-reference",
                    payload = "{\"version\":1}",
                )
                TestDB.database.exposedTransaction {
                    markOutboxMessageIrrelevant(original.uuid)
                }
                val rescheduledAt = Instant.parse("2026-09-09T10:00:00Z")

                val reactivated = TestDB.database.exposedTransaction {
                    reactivateIrrelevantOutboxMessage(
                        NewOutboxMessage(
                            messageType = TEST_SCHEDULED_MESSAGE,
                            dedupKey = "reminder-opt-in",
                            externalRef = "current-reference",
                            payload = "{\"version\":2}",
                            scheduledAt = rescheduledAt,
                        ),
                    )
                }
                val persisted = TestDB.database.exposedTransaction {
                    findOutboxMessage(TEST_SCHEDULED_MESSAGE, "reminder-opt-in")
                }

                reactivated shouldBe true
                persisted?.uuid shouldBe original.uuid
                persisted?.status shouldBe OutboxStatus.READY
                persisted?.externalRef shouldBe "current-reference"
                persisted?.payload shouldBe "{\"version\": 2}"
                persisted?.scheduledAt shouldBe rescheduledAt
            }

            it("never reactivates a message that was already sent") {
                val sent = TestDB.database.enqueueTestOutboxMessage(dedupKey = "already-sent")
                TestDB.database.exposedTransaction {
                    markOutboxMessageSent(sent.uuid, Instant.parse("2026-08-12T12:00:00Z"))
                }

                val reactivated = TestDB.database.exposedTransaction {
                    reactivateIrrelevantOutboxMessage(
                        NewOutboxMessage(
                            messageType = TEST_IMMEDIATE_MESSAGE,
                            dedupKey = "already-sent",
                            externalRef = "reference",
                            payload = "{}",
                            scheduledAt = Instant.EPOCH,
                        ),
                    )
                }

                reactivated shouldBe false
                TestDB.database.exposedTransaction {
                    findOutboxMessage(sent.uuid)
                }?.status shouldBe OutboxStatus.SENT
            }

            it("allows the same dedup key for different message types") {
                val immediate = TestDB.database.enqueueTestOutboxMessage(
                    messageType = TEST_IMMEDIATE_MESSAGE,
                    dedupKey = "shared-reference",
                )
                val scheduled = TestDB.database.enqueueTestOutboxMessage(
                    messageType = TEST_SCHEDULED_MESSAGE,
                    dedupKey = "shared-reference",
                )

                immediate.messageType shouldBe TEST_IMMEDIATE_MESSAGE
                scheduled.messageType shouldBe TEST_SCHEDULED_MESSAGE
                (immediate.uuid == scheduled.uuid) shouldBe false
            }

            it("fails instead of silently ignoring a uuid collision for another command") {
                val sharedUuid = UUID.randomUUID()
                TestDB.database.enqueueTestOutboxMessage(
                    uuid = sharedUuid,
                    messageType = TEST_IMMEDIATE_MESSAGE,
                    dedupKey = "first-command",
                )

                shouldThrow<Exception> {
                    TestDB.database.exposedTransaction {
                        enqueueOutboxMessage(
                            NewOutboxMessage(
                                uuid = sharedUuid,
                                messageType = TEST_SCHEDULED_MESSAGE,
                                dedupKey = "second-command",
                                externalRef = "another-reference",
                                payload = "{}",
                                scheduledAt = Instant.EPOCH,
                            ),
                        )
                    }
                }

                TestDB.database.exposedTransaction {
                    findOutboxMessage(TEST_SCHEDULED_MESSAGE, "second-command")
                }.shouldBeNull()
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
                            enqueueOutboxMessage(
                                NewOutboxMessage(
                                    messageType = TEST_IMMEDIATE_MESSAGE,
                                    dedupKey = dedupKey,
                                    externalRef = "first-reference",
                                    payload = "{}",
                                    scheduledAt = Instant.EPOCH,
                                ),
                            ).also {
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
                            enqueueOutboxMessage(
                                NewOutboxMessage(
                                    messageType = TEST_IMMEDIATE_MESSAGE,
                                    dedupKey = dedupKey,
                                    externalRef = "second-reference",
                                    payload = "{}",
                                    scheduledAt = Instant.EPOCH,
                                ),
                            )
                        }
                    }
                    secondAttemptStarted.await()
                    delay(250)
                    allowFirstCommit.countDown()

                    first.await() shouldBe true
                    second.await() shouldBe false
                }

                secondAttempts.get() shouldBe 2
                TestDB.database.exposedTransaction {
                    findOutboxMessage(TEST_IMMEDIATE_MESSAGE, dedupKey)
                }?.externalRef shouldBe "first-reference"
            }

            it("rejects malformed JSON payloads in the database") {
                shouldThrow<Exception> {
                    TestDB.database.exposedTransaction {
                        enqueueOutboxMessage(
                            NewOutboxMessage(
                                uuid = UUID.randomUUID(),
                                messageType = TEST_IMMEDIATE_MESSAGE,
                                dedupKey = "invalid-json",
                                externalRef = "reference",
                                payload = "not-json",
                                scheduledAt = Instant.EPOCH,
                            ),
                        )
                    }
                }
            }
        }

        describe("claimNextReadyOutboxMessage") {
            it("claims only due messages for the requested type") {
                val now = Instant.parse("2026-08-12T12:00:00Z")
                val due = TestDB.database.enqueueTestOutboxMessage(
                    messageType = TEST_SCHEDULED_MESSAGE,
                    scheduledAt = now.minusSeconds(1),
                )
                TestDB.database.enqueueTestOutboxMessage(
                    messageType = TEST_SCHEDULED_MESSAGE,
                    scheduledAt = now.plusSeconds(1),
                )
                TestDB.database.enqueueTestOutboxMessage(
                    messageType = TEST_IMMEDIATE_MESSAGE,
                    scheduledAt = now.minusSeconds(2),
                )

                val claimed = TestDB.database.exposedTransaction {
                    claimNextReadyOutboxMessage(TEST_SCHEDULED_MESSAGE, now)
                }

                claimed?.uuid shouldBe due.uuid
            }

            it("returns null for an empty type-specific queue") {
                TestDB.database.enqueueTestOutboxMessage(messageType = TEST_IMMEDIATE_MESSAGE)

                val claimed = TestDB.database.exposedTransaction {
                    claimNextReadyOutboxMessage(TEST_SCHEDULED_MESSAGE, Instant.now())
                }

                claimed.shouldBeNull()
            }
        }

        describe("OutboxMessageType") {
            it("accepts new domain message types without a core enum change") {
                val evaluationReminder = no.nav.syfo.application.outbox.domain.OutboxMessageType(
                    "OPPFOLGINGSPLAN_EVALUATION_REMINDER",
                )

                val persisted = TestDB.database.enqueueTestOutboxMessage(messageType = evaluationReminder)

                TestDB.database.exposedTransaction {
                    findOutboxMessage(evaluationReminder, persisted.dedupKey)
                }?.messageType shouldBe evaluationReminder
            }

            it("rejects invalid database identifiers early") {
                shouldThrow<IllegalArgumentException> {
                    no.nav.syfo.application.outbox.domain.OutboxMessageType("invalid type")
                }
            }
        }
    })
