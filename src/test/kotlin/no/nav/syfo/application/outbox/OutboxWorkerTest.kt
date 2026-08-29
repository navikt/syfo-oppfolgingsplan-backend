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
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY
import no.nav.syfo.application.outbox.db.findOutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxCancellationReason
import no.nav.syfo.application.outbox.domain.OutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import no.nav.syfo.application.outbox.domain.OutboxStatus
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Duration.Companion.INFINITE

class OutboxWorkerTest :
    DescribeSpec({
        val now = Instant.parse("2026-08-12T12:00:00Z")
        val fixedClock = Clock.fixed(now, ZoneOffset.UTC)

        beforeTest { TestDB.clearAllData() }

        describe("domain outcomes") {
            it("observes a staged message type without claiming it") {
                val staged = TestDB.database.enqueueTestOutboxMessage(
                    messageType = TEST_STAGED_MESSAGE,
                    availableAt = now.minusSeconds(1),
                )

                OutboxWorker(
                    database = TestDB.database,
                    handlers = listOf(TestOutboxHandler()),
                    clock = fixedClock,
                    observedMessageTypes = listOf(TEST_IMMEDIATE_MESSAGE, TEST_STAGED_MESSAGE),
                ).runOnce() shouldBe OutboxBatchResult()

                TestDB.database.findOutboxMessage(staged)?.status shouldBe OutboxStatus.READY
                METRICS_REGISTRY.find("${METRICS_NS}_outbox_due_ready")
                    .tag("message_type", TEST_STAGED_MESSAGE.value)
                    .gauge()
                    .shouldNotBeNull()
                    .value() shouldBe 1.0
            }

            it("marks an acknowledged message sent") {
                val message = TestDB.database.enqueueTestOutboxMessage()
                val handler = TestOutboxHandler()
                val lifecycleMetrics = RecordingOutboxLifecycleMetrics()

                val result = OutboxWorker(
                    database = TestDB.database,
                    handlers = listOf(handler),
                    clock = fixedClock,
                    lifecycleMetrics = lifecycleMetrics,
                ).runOnce()

                result shouldBe OutboxBatchResult(sent = 1)
                handler.handledMessages shouldBe listOf(message.uuid)
                lifecycleMetrics.terminalOutcomes shouldBe listOf(message.uuid to OutboxResult.Sent)
                val persisted = TestDB.database.findOutboxMessage(message).shouldNotBeNull()
                persisted.status shouldBe OutboxStatus.SENT
                persisted.completedAt.shouldNotBeNull()
                persisted.claimToken.shouldBeNull()
                persisted.leaseUntil.shouldBeNull()
            }

            it("cancels a message with a general domain reason") {
                val message = TestDB.database.enqueueTestOutboxMessage()
                val outcome = OutboxResult.Cancelled(OutboxCancellationReason.SOURCE_NO_LONGER_ELIGIBLE)
                val handler = TestOutboxHandler(
                    outcome = { _, _ -> outcome },
                )
                val lifecycleMetrics = RecordingOutboxLifecycleMetrics()

                val result = OutboxWorker(
                    database = TestDB.database,
                    handlers = listOf(handler),
                    clock = fixedClock,
                    lifecycleMetrics = lifecycleMetrics,
                ).runOnce()

                result shouldBe OutboxBatchResult(cancelled = 1)
                lifecycleMetrics.terminalOutcomes shouldBe listOf(message.uuid to outcome)
                val persisted = TestDB.database.findOutboxMessage(message).shouldNotBeNull()
                persisted.status shouldBe OutboxStatus.CANCELLED
                persisted.cancellationReason shouldBe OutboxCancellationReason.SOURCE_NO_LONGER_ELIGIBLE
                persisted.failureCount shouldBeExactly 0
            }

            it("defers domain evaluation and continues the claimed batch") {
                val deferred = TestDB.database.enqueueTestOutboxMessage(availableAt = now.minusSeconds(2))
                val next = TestDB.database.enqueueTestOutboxMessage(availableAt = now.minusSeconds(1))
                val retryEvaluationAt = now.plusSeconds(3600)
                val handler = TestOutboxHandler(
                    outcome = { message, _ ->
                        if (message.uuid == deferred.uuid) OutboxResult.Deferred(retryEvaluationAt) else OutboxResult.Sent
                    },
                )
                val lifecycleMetrics = RecordingOutboxLifecycleMetrics()

                val result = OutboxWorker(
                    database = TestDB.database,
                    handlers = listOf(handler),
                    clock = fixedClock,
                    lifecycleMetrics = lifecycleMetrics,
                ).runOnce()

                result shouldBe OutboxBatchResult(sent = 1, deferred = 1)
                lifecycleMetrics.terminalOutcomes shouldBe listOf(next.uuid to OutboxResult.Sent)
                val persistedDeferred = TestDB.database.findOutboxMessage(deferred).shouldNotBeNull()
                persistedDeferred.status shouldBe OutboxStatus.READY
                persistedDeferred.availableAt shouldBe retryEvaluationAt
                persistedDeferred.failureCount shouldBeExactly 0
                TestDB.database.findOutboxMessage(next)?.status shouldBe OutboxStatus.SENT
            }

            it("treats a non-future domain deferral as a retryable handler error") {
                val message = TestDB.database.enqueueTestOutboxMessage()
                val handler = TestOutboxHandler(outcome = { _, _ -> OutboxResult.Deferred(now) })

                val result = OutboxWorker(TestDB.database, listOf(handler), fixedClock).runOnce()

                result shouldBe OutboxBatchResult(retryScheduled = 1)
                val persisted = TestDB.database.findOutboxMessage(message).shouldNotBeNull()
                persisted.status shouldBe OutboxStatus.READY
                persisted.failureCount shouldBeExactly 1
                persisted.availableAt shouldBeAfter now
            }

            it("does not claim a message before it becomes available") {
                val message = TestDB.database.enqueueTestOutboxMessage(availableAt = now.plusSeconds(1))
                val handler = TestOutboxHandler()

                OutboxWorker(TestDB.database, listOf(handler), fixedClock).runOnce() shouldBe OutboxBatchResult()

                handler.handledMessages shouldBe emptyList()
                TestDB.database.findOutboxMessage(message)?.status shouldBe OutboxStatus.READY
            }
        }

        describe("technical failure and recovery") {
            it("records a handler failure and applies the handler retry policy") {
                val message = TestDB.database.enqueueTestOutboxMessage()
                val retryAt = now.plusSeconds(15 * 60)
                val handler = TestOutboxHandler(
                    retryPolicy = OutboxRetryPolicy { _, _ -> retryAt },
                    outcome = { _, _ -> error("broker unavailable") },
                )

                val result = OutboxWorker(TestDB.database, listOf(handler), fixedClock).runOnce()

                result shouldBe OutboxBatchResult(retryScheduled = 1)
                val persisted = TestDB.database.findOutboxMessage(message).shouldNotBeNull()
                persisted.status shouldBe OutboxStatus.READY
                persisted.failureCount shouldBeExactly 1
                persisted.lastFailureAt shouldBe now
                persisted.availableAt shouldBe retryAt
                persisted.claimToken.shouldBeNull()
            }

            it("continues after one poison message") {
                val failed = TestDB.database.enqueueTestOutboxMessage(availableAt = now.minusSeconds(2))
                val successful = TestDB.database.enqueueTestOutboxMessage(availableAt = now.minusSeconds(1))
                val handler = TestOutboxHandler(
                    outcome = { message, _ ->
                        if (message.uuid == failed.uuid) error("poison message")
                        OutboxResult.Sent
                    },
                )

                val result = OutboxWorker(TestDB.database, listOf(handler), fixedClock).runOnce()

                result shouldBe OutboxBatchResult(sent = 1, retryScheduled = 1)
                TestDB.database.findOutboxMessage(failed)?.status shouldBe OutboxStatus.READY
                TestDB.database.findOutboxMessage(successful)?.status shouldBe OutboxStatus.SENT
            }

            it("aborts after systemic failures and leaves unprocessed claims for lease recovery") {
                val messages = (1..4).map { position ->
                    TestDB.database.enqueueTestOutboxMessage(
                        availableAt = now.minusSeconds((5 - position).toLong()),
                    )
                }
                val handler = TestOutboxHandler(outcome = { _, _ -> error("downstream unavailable") })
                val config = OutboxWorkerConfig(maxConsecutiveFailures = 3)

                val result = OutboxWorker(TestDB.database, listOf(handler), fixedClock, config).runOnce()

                result shouldBe OutboxBatchResult(retryScheduled = 3)
                messages.take(3).forEach { message ->
                    val persisted = TestDB.database.findOutboxMessage(message).shouldNotBeNull()
                    persisted.status shouldBe OutboxStatus.READY
                    persisted.failureCount shouldBeExactly 1
                }
                TestDB.database.findOutboxMessage(messages.last())?.status shouldBe OutboxStatus.CLAIMED
            }

            it("leaves a cancelled worker claim for recovery after its lease expires") {
                val message = TestDB.database.enqueueTestOutboxMessage()
                val handler = TestOutboxHandler(outcome = { _, _ -> throw CancellationException("stopped") })

                shouldThrow<CancellationException> {
                    OutboxWorker(TestDB.database, listOf(handler), fixedClock).runOnce()
                }

                val claimed = TestDB.database.findOutboxMessage(message).shouldNotBeNull()
                claimed.status shouldBe OutboxStatus.CLAIMED
                claimed.failureCount shouldBeExactly 0

                val recoveryClock = Clock.fixed(now.plusSeconds(5 * 60), ZoneOffset.UTC)
                OutboxWorker(TestDB.database, listOf(TestOutboxHandler()), recoveryClock).runOnce() shouldBe
                    OutboxBatchResult(sent = 1)
                TestDB.database.findOutboxMessage(message)?.status shouldBe OutboxStatus.SENT
            }

            it("ignores a stale completion after another worker reclaims the lease") {
                val clock = MutableClock(now)
                val message = TestDB.database.enqueueTestOutboxMessage()
                val handler = TestOutboxHandler(
                    outcome = { _, _ ->
                        clock.advance(Duration.ofMinutes(5))
                        TestDB.database.claim(clock.instant()).single()
                        OutboxResult.Sent
                    },
                )
                val lifecycleMetrics = RecordingOutboxLifecycleMetrics()

                val result = OutboxWorker(
                    database = TestDB.database,
                    handlers = listOf(handler),
                    clock = clock,
                    lifecycleMetrics = lifecycleMetrics,
                ).runOnce()

                result shouldBe OutboxBatchResult(claimLost = 1)
                lifecycleMetrics.terminalOutcomes shouldBe emptyList()
                val persisted = TestDB.database.findOutboxMessage(message).shouldNotBeNull()
                persisted.status shouldBe OutboxStatus.CLAIMED
                persisted.claimToken.shouldNotBeNull()
            }
        }

        describe("replica coordination and lease budget") {
            it("does not let concurrent processors handle the same active claim") {
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
                        OutboxWorker(TestDB.database, listOf(firstHandler), fixedClock).runOnce()
                    }
                    claimed.await()
                    val second = async {
                        OutboxWorker(TestDB.database, listOf(secondHandler), fixedClock).runOnce()
                    }

                    second.await() shouldBe OutboxBatchResult()
                    release.complete(Unit)
                    first.await() shouldBe OutboxBatchResult(sent = 1)
                }

                firstHandler.handledMessages.size shouldBeExactly 1
                secondHandler.handledMessages.size shouldBeExactly 0
            }

            it("stops starting messages when the lease budget is spent") {
                val first = TestDB.database.enqueueTestOutboxMessage(availableAt = now.minusSeconds(2))
                val second = TestDB.database.enqueueTestOutboxMessage(availableAt = now.minusSeconds(1))
                val clock = MutableClock(now)
                val handler = TestOutboxHandler(
                    outcome = { message, _ ->
                        if (message.uuid == first.uuid) clock.advance(Duration.ofMinutes(4))
                        OutboxResult.Sent
                    },
                )

                val result = OutboxWorker(TestDB.database, listOf(handler), clock).runOnce()

                result shouldBe OutboxBatchResult(sent = 1)
                TestDB.database.findOutboxMessage(first)?.status shouldBe OutboxStatus.SENT
                TestDB.database.findOutboxMessage(second)?.status shouldBe OutboxStatus.CLAIMED
                handler.handledMessages shouldBe listOf(first.uuid)
            }

            it("rejects duplicate handlers for one message type") {
                shouldThrow<IllegalArgumentException> {
                    OutboxWorker(
                        TestDB.database,
                        listOf(TestOutboxHandler(), TestOutboxHandler()),
                        fixedClock,
                    )
                }
            }

            it("rejects distinct adapter types with the same stable database value") {
                val duplicateType = object : OutboxMessageType {
                    override val value = TEST_IMMEDIATE_MESSAGE.value
                }

                shouldThrow<IllegalArgumentException> {
                    OutboxWorker(
                        TestDB.database,
                        listOf(TestOutboxHandler(), TestOutboxHandler(messageType = duplicateType)),
                        fixedClock,
                    )
                }
            }

            it("rejects an infinite lease before the worker starts") {
                shouldThrow<IllegalArgumentException> {
                    OutboxWorkerConfig(leaseDuration = INFINITE)
                }
            }
        }
    })

private class RecordingOutboxLifecycleMetrics : OutboxLifecycleMetrics {
    val terminalOutcomes = mutableListOf<Pair<UUID, OutboxResult>>()

    override fun recordEnqueued(
        messageType: OutboxMessageType,
        count: Int,
    ) = Unit

    override fun recordTerminal(
        message: OutboxMessage,
        outcome: OutboxResult,
        completedAt: Instant,
    ) {
        terminalOutcomes += message.uuid to outcome
    }
}
