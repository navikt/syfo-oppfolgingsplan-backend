package no.nav.syfo.application.outbox

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import no.nav.syfo.TestDB
import no.nav.syfo.application.outbox.db.findOutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxCancellationReason
import no.nav.syfo.application.outbox.domain.OutboxStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class OutboxProcessorTest :
    DescribeSpec({
        val now = Instant.parse("2026-08-12T12:00:00Z")
        val clock = Clock.fixed(now, ZoneOffset.UTC)

        beforeTest { TestDB.clearAllData() }

        describe("domain outcomes") {
            it("marks an acknowledged message sent") {
                val message = TestDB.database.enqueueTestOutboxMessage()
                val handler = TestOutboxHandler()

                val result = OutboxProcessor(TestDB.database, listOf(handler), clock).processReadyMessages()

                result shouldBe OutboxBatchResult(sent = 1)
                handler.handledMessages shouldBe listOf(message.uuid)
                TestDB.database.findOutboxMessage(message.uuid)?.status shouldBe OutboxStatus.SENT
                TestDB.database.findOutboxMessage(message.uuid)?.sentAt.shouldNotBeNull()
            }

            it("cancels a message with its domain reason without counting a technical failure") {
                val message = TestDB.database.enqueueTestOutboxMessage()
                val handler = TestOutboxHandler(
                    outcome = { _, _ -> OutboxResult.Cancelled(OutboxCancellationReason.PLAN_ALREADY_CREATED) },
                )

                val result = OutboxProcessor(TestDB.database, listOf(handler), clock).processReadyMessages()

                result shouldBe OutboxBatchResult(cancelled = 1)
                val persisted = TestDB.database.findOutboxMessage(message.uuid).shouldNotBeNull()
                persisted.status shouldBe OutboxStatus.CANCELLED
                persisted.cancellationReason shouldBe OutboxCancellationReason.PLAN_ALREADY_CREATED
                persisted.attemptCount shouldBeExactly 0
            }

            it("defers domain evaluation until the handler-provided time and continues the batch") {
                val deferred = TestDB.database.enqueueTestOutboxMessage(
                    scheduledAt = now.minusSeconds(2),
                )
                val next = TestDB.database.enqueueTestOutboxMessage(
                    scheduledAt = now.minusSeconds(1),
                )
                val retryEvaluationAt = now.plusSeconds(3600)
                val handler = TestOutboxHandler(
                    outcome = { message, _ ->
                        if (message.uuid == deferred.uuid) OutboxResult.Deferred(retryEvaluationAt) else OutboxResult.Sent
                    },
                )

                val result = OutboxProcessor(TestDB.database, listOf(handler), clock).processReadyMessages()

                result shouldBe OutboxBatchResult(sent = 1, deferred = 1)
                val persistedDeferred = TestDB.database.findOutboxMessage(deferred.uuid).shouldNotBeNull()
                persistedDeferred.status shouldBe OutboxStatus.READY
                persistedDeferred.scheduledAt shouldBe retryEvaluationAt
                persistedDeferred.attemptCount shouldBeExactly 0
                TestDB.database.findOutboxMessage(next.uuid)?.status shouldBe OutboxStatus.SENT
            }

            it("treats a non-future domain deferral as a retryable handler error") {
                val message = TestDB.database.enqueueTestOutboxMessage()
                val handler = TestOutboxHandler(outcome = { _, _ -> OutboxResult.Deferred(now) })

                val result = OutboxProcessor(TestDB.database, listOf(handler), clock).processReadyMessages()

                result shouldBe OutboxBatchResult(retryScheduled = 1)
                val persisted = TestDB.database.findOutboxMessage(message.uuid).shouldNotBeNull()
                persisted.status shouldBe OutboxStatus.READY
                persisted.attemptCount shouldBeExactly 1
                persisted.scheduledAt shouldBeAfter now
            }

            it("does not process a message before its scheduled time") {
                val message = TestDB.database.enqueueTestOutboxMessage(scheduledAt = now.plusSeconds(1))
                val handler = TestOutboxHandler()

                OutboxProcessor(TestDB.database, listOf(handler), clock).processReadyMessages() shouldBe OutboxBatchResult()

                handler.handledMessages shouldBe emptyList()
                TestDB.database.findOutboxMessage(message.uuid)?.status shouldBe OutboxStatus.READY
            }
        }

        describe("technical failure retry") {
            it("rolls back handler writes, records the attempt, and uses the handler retry policy") {
                val message = TestDB.database.enqueueTestOutboxMessage(payload = "{}")
                val retryAt = now.plusSeconds(15 * 60)
                val handler = TestOutboxHandler(
                    retryPolicy = OutboxRetryPolicy { _, _ -> retryAt },
                    outcome = { current, _ ->
                        exec(
                            "UPDATE outbox SET payload = '{\"mutated\":true}'::jsonb WHERE uuid = '${current.uuid}'",
                        )
                        error("broker unavailable")
                    },
                )

                val result = OutboxProcessor(TestDB.database, listOf(handler), clock).processReadyMessages()

                result shouldBe OutboxBatchResult(retryScheduled = 1)
                val persisted = TestDB.database.findOutboxMessage(message.uuid).shouldNotBeNull()
                persisted.status shouldBe OutboxStatus.READY
                persisted.payload shouldBe "{}"
                persisted.attemptCount shouldBeExactly 1
                persisted.lastAttemptAt shouldBe now
                persisted.scheduledAt shouldBe retryAt
                persisted.sentAt.shouldBeNull()
            }

            it("continues with later messages after a handler failure") {
                val failed = TestDB.database.enqueueTestOutboxMessage(scheduledAt = now.minusSeconds(2))
                val successful = TestDB.database.enqueueTestOutboxMessage(scheduledAt = now.minusSeconds(1))
                val handler = TestOutboxHandler(
                    outcome = { message, _ ->
                        if (message.uuid == failed.uuid) error("poison message")
                        OutboxResult.Sent
                    },
                )

                val result = OutboxProcessor(TestDB.database, listOf(handler), clock).processReadyMessages()

                result shouldBe OutboxBatchResult(sent = 1, retryScheduled = 1)
                TestDB.database.findOutboxMessage(failed.uuid)?.status shouldBe OutboxStatus.READY
                TestDB.database.findOutboxMessage(successful.uuid)?.status shouldBe OutboxStatus.SENT
            }

            it("rethrows cancellation without recording a failed attempt") {
                val message = TestDB.database.enqueueTestOutboxMessage()
                val handler = TestOutboxHandler(outcome = { _, _ -> throw CancellationException("stopped") })
                val processor = OutboxProcessor(TestDB.database, listOf(handler), clock)

                shouldThrow<CancellationException> {
                    processor.processReadyMessages()
                }

                val persisted = TestDB.database.findOutboxMessage(message.uuid).shouldNotBeNull()
                persisted.status shouldBe OutboxStatus.READY
                persisted.attemptCount shouldBeExactly 0
                persisted.lastAttemptAt.shouldBeNull()
            }
        }

        describe("multiple adapters and workers") {
            it("processes every registered message type independently") {
                val immediate = TestDB.database.enqueueTestOutboxMessage(messageType = TEST_IMMEDIATE_MESSAGE)
                val scheduled = TestDB.database.enqueueTestOutboxMessage(messageType = TEST_SCHEDULED_MESSAGE)
                val immediateHandler = TestOutboxHandler(TEST_IMMEDIATE_MESSAGE)
                val scheduledHandler = TestOutboxHandler(TEST_SCHEDULED_MESSAGE)

                val result = OutboxProcessor(
                    TestDB.database,
                    listOf(immediateHandler, scheduledHandler),
                    clock,
                ).processReadyMessages()

                result shouldBe OutboxBatchResult(sent = 2)
                immediateHandler.handledMessages shouldBe listOf(immediate.uuid)
                scheduledHandler.handledMessages shouldBe listOf(scheduled.uuid)
            }

            it("does not let concurrent processors handle the same message") {
                TestDB.database.enqueueTestOutboxMessage()
                val claimed = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val firstHandler = TestOutboxHandler(
                    outcome = { _, _ ->
                        claimed.complete(Unit)
                        release.await()
                        OutboxResult.Sent
                    },
                )
                val secondHandler = TestOutboxHandler()

                coroutineScope {
                    val first = async {
                        OutboxProcessor(TestDB.database, listOf(firstHandler), clock).processReadyMessages()
                    }
                    claimed.await()
                    val second = async {
                        OutboxProcessor(TestDB.database, listOf(secondHandler), clock).processReadyMessages()
                    }

                    second.await() shouldBe OutboxBatchResult()
                    release.complete(Unit)
                    first.await() shouldBe OutboxBatchResult(sent = 1)
                }

                firstHandler.handledMessages.size shouldBeExactly 1
                secondHandler.handledMessages.size shouldBeExactly 0
            }

            it("rejects duplicate handlers for one message type") {
                shouldThrow<IllegalArgumentException> {
                    OutboxProcessor(
                        TestDB.database,
                        listOf(TestOutboxHandler(), TestOutboxHandler()),
                        clock,
                    )
                }
            }
        }
    })
