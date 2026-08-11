package no.nav.syfo.application.outbox

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import no.nav.budstikka.contract.Budstikka
import no.nav.budstikka.contract.EncodedDispatch
import no.nav.budstikka.contract.EventId
import no.nav.budstikka.contract.PersonIdentifier
import no.nav.budstikka.contract.SendingWindow
import no.nav.budstikka.contract.Varseltype
import no.nav.syfo.TestDB
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.exposedTransaction
import no.nav.syfo.application.outbox.db.findOutboxMessage
import no.nav.syfo.application.outbox.db.insertOutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import no.nav.syfo.application.outbox.domain.OutboxStatus
import no.nav.syfo.defaultPersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.db.OppfolgingsplanOutboxTable
import no.nav.syfo.oppfolgingsplan.outbox.LegacyOppfolgingsplanOutboxReconciler
import no.nav.syfo.oppfolgingsplan.outbox.OPPFOLGINGSPLAN_CREATED_BUDSTIKKA_TEXT
import no.nav.syfo.oppfolgingsplan.outbox.OppfolgingsplanCreatedOutboxHandler
import no.nav.syfo.persistOppfolgingsplan
import no.nav.syfo.varsel.budstikka.infrastructure.BudstikkaPublisher
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class OutboxProcessorTest :
    DescribeSpec({
        beforeTest {
            TestDB.clearAllData()
        }

        describe("processReadyMessages") {
            it("publishes a ready message and marks it sent after acknowledgement") {
                val plan = defaultPersistedOppfolgingsplan()
                val planUuid = TestDB.database.persistOppfolgingsplan(plan)
                val messageUuid = UUID.randomUUID()
                TestDB.database.insertTestOutboxMessage(messageUuid, planUuid)
                val publishedDispatch = slot<EncodedDispatch>()
                val publisher = mockk<BudstikkaPublisher>()
                coEvery { publisher.publish(capture(publishedDispatch)) } just runs
                val processor = OutboxProcessor(
                    database = TestDB.database,
                    handlers = listOf(
                        OppfolgingsplanCreatedOutboxHandler(
                            publisher = publisher,
                            oppfolgingsplanUrl = OPPFOLGINGSPLAN_URL,
                        ),
                    ),
                )

                val firstResult = processor.processReadyMessages()
                val secondResult = processor.processReadyMessages()

                firstResult shouldBe OutboxBatchResult(sent = 1)
                secondResult.processed shouldBeExactly 0
                val persistedMessage = TestDB.database.findTestOutboxMessage(planUuid)
                persistedMessage?.status shouldBe OutboxStatus.SENT
                persistedMessage?.sentAt.shouldNotBeNull()
                val expectedDispatch = Budstikka.brukervarselCreate(
                    eventId = EventId(messageUuid),
                    reference = planUuid.toString(),
                    sykmeldt = PersonIdentifier(plan.sykmeldtFnr),
                    varseltype = Varseltype.BESKJED,
                    text = OPPFOLGINGSPLAN_CREATED_BUDSTIKKA_TEXT,
                    link = OPPFOLGINGSPLAN_URL,
                    sendingWindow = SendingWindow.ONGOING,
                )
                publishedDispatch.captured.topic shouldBe expectedDispatch.topic
                publishedDispatch.captured.key shouldBe expectedDispatch.key
                publishedDispatch.captured.value shouldBe expectedDispatch.value
                publishedDispatch.captured.headerBytes().mapValues { it.value.toList() } shouldBe
                    expectedDispatch.headerBytes().mapValues { it.value.toList() }
                coVerify(exactly = 1) { publisher.publish(any()) }
            }

            it("rolls back and leaves the message ready when publishing fails") {
                val planUuid = TestDB.database.persistOppfolgingsplan(defaultPersistedOppfolgingsplan())
                TestDB.database.insertTestOutboxMessage(UUID.randomUUID(), planUuid)
                val publisher = mockk<BudstikkaPublisher>()
                coEvery { publisher.publish(any()) } throws RuntimeException("broker unavailable")
                val processor = OutboxProcessor(
                    database = TestDB.database,
                    handlers = listOf(
                        OppfolgingsplanCreatedOutboxHandler(
                            publisher = publisher,
                            oppfolgingsplanUrl = OPPFOLGINGSPLAN_URL,
                        ),
                    ),
                )

                val result = processor.processReadyMessages()

                result shouldBe OutboxBatchResult(failed = 1)
                val persistedMessage = TestDB.database.findTestOutboxMessage(planUuid).shouldNotBeNull()
                persistedMessage.status shouldBe OutboxStatus.READY
                persistedMessage.attemptCount shouldBeExactly 1
                persistedMessage.lastAttemptAt.shouldNotBeNull()
                persistedMessage.scheduledAt shouldBeAfter Instant.now()
                persistedMessage.sentAt.shouldBeNull()
            }

            it("quarantines a message after the configured number of attempts") {
                val planUuid = TestDB.database.persistOppfolgingsplan(defaultPersistedOppfolgingsplan())
                TestDB.database.insertTestOutboxMessage(UUID.randomUUID(), planUuid)
                val publisher = mockk<BudstikkaPublisher>()
                coEvery { publisher.publish(any()) } throws RuntimeException("invalid dispatch")
                val processor = OutboxProcessor(
                    database = TestDB.database,
                    handlers = listOf(
                        OppfolgingsplanCreatedOutboxHandler(
                            publisher = publisher,
                            oppfolgingsplanUrl = OPPFOLGINGSPLAN_URL,
                        ),
                    ),
                    maxAttempts = 1,
                )

                processor.processReadyMessages() shouldBe OutboxBatchResult(failed = 1)

                TestDB.database.findTestOutboxMessage(planUuid)?.status shouldBe OutboxStatus.FAILED
            }

            it("rolls back without counting coroutine cancellation as a failed attempt") {
                val planUuid = TestDB.database.persistOppfolgingsplan(defaultPersistedOppfolgingsplan())
                TestDB.database.insertTestOutboxMessage(UUID.randomUUID(), planUuid)
                val publisher = mockk<BudstikkaPublisher>()
                coEvery { publisher.publish(any()) } throws CancellationException("task stopped")
                val processor = OutboxProcessor(
                    database = TestDB.database,
                    handlers = listOf(
                        OppfolgingsplanCreatedOutboxHandler(
                            publisher = publisher,
                            oppfolgingsplanUrl = OPPFOLGINGSPLAN_URL,
                        ),
                    ),
                )

                shouldThrow<CancellationException> {
                    processor.processReadyMessages()
                }

                val persistedMessage = TestDB.database.findTestOutboxMessage(planUuid).shouldNotBeNull()
                persistedMessage.status shouldBe OutboxStatus.READY
                persistedMessage.attemptCount shouldBeExactly 0
                persistedMessage.lastAttemptAt.shouldBeNull()
            }

            it("continues with later messages when the oldest message fails") {
                val firstMessageUuid = UUID.randomUUID()
                val secondMessageUuid = UUID.randomUUID()
                val firstExternalRef = UUID.randomUUID()
                val secondExternalRef = UUID.randomUUID()
                val scheduledAt = Instant.now().minusSeconds(10)
                TestDB.database.insertTestOutboxMessage(firstMessageUuid, firstExternalRef, scheduledAt)
                TestDB.database.insertTestOutboxMessage(secondMessageUuid, secondExternalRef, scheduledAt.plusMillis(1))
                val handler = object : OutboxMessageHandler {
                    override val messageType = OutboxMessageType.OPPFOLGINGSPLAN_OPPRETTET

                    override suspend fun process(
                        transaction: JdbcTransaction,
                        message: OutboxMessage,
                    ): OutboxResult {
                        if (message.uuid == firstMessageUuid) {
                            throw RuntimeException("poison message")
                        }
                        return OutboxResult.SENT
                    }
                }

                val result = OutboxProcessor(TestDB.database, listOf(handler)).processReadyMessages()

                result shouldBe OutboxBatchResult(sent = 1, failed = 1)
                TestDB.database.findTestOutboxMessage(firstExternalRef)?.status shouldBe OutboxStatus.READY
                TestDB.database.findTestOutboxMessage(secondExternalRef)?.status shouldBe OutboxStatus.SENT
            }

            it("marks a message irrelevant when the referenced plan no longer exists") {
                val missingPlanUuid = UUID.randomUUID()
                TestDB.database.insertTestOutboxMessage(UUID.randomUUID(), missingPlanUuid)
                val publisher = mockk<BudstikkaPublisher>(relaxed = true)
                val processor = OutboxProcessor(
                    database = TestDB.database,
                    handlers = listOf(
                        OppfolgingsplanCreatedOutboxHandler(
                            publisher = publisher,
                            oppfolgingsplanUrl = OPPFOLGINGSPLAN_URL,
                        ),
                    ),
                )

                val result = processor.processReadyMessages()

                result shouldBe OutboxBatchResult(irrelevant = 1)
                TestDB.database.findTestOutboxMessage(missingPlanUuid)?.status shouldBe OutboxStatus.IRRELEVANT
                coVerify(exactly = 0) { publisher.publish(any()) }
            }

            it("marks a message irrelevant when the referenced plan is hidden") {
                val hiddenPlan = defaultPersistedOppfolgingsplan().copy(skjultFra = Instant.now())
                val hiddenPlanUuid = TestDB.database.persistOppfolgingsplan(hiddenPlan)
                TestDB.database.insertTestOutboxMessage(UUID.randomUUID(), hiddenPlanUuid)
                val publisher = mockk<BudstikkaPublisher>(relaxed = true)
                val processor = OutboxProcessor(
                    database = TestDB.database,
                    handlers = listOf(
                        OppfolgingsplanCreatedOutboxHandler(
                            publisher = publisher,
                            oppfolgingsplanUrl = OPPFOLGINGSPLAN_URL,
                        ),
                    ),
                )

                processor.processReadyMessages() shouldBe OutboxBatchResult(irrelevant = 1)

                TestDB.database.findTestOutboxMessage(hiddenPlanUuid)?.status shouldBe OutboxStatus.IRRELEVANT
                coVerify(exactly = 0) { publisher.publish(any()) }
            }

            it("reconciles messages created by old application instances during rolling deployment") {
                val plan = defaultPersistedOppfolgingsplan()
                val planUuid = TestDB.database.persistOppfolgingsplan(plan)
                val eventId = TestDB.database.findEventId(planUuid).shouldNotBeNull()
                val publisher = mockk<BudstikkaPublisher>()
                coEvery { publisher.publish(any()) } just runs
                val processor = OutboxProcessor(
                    database = TestDB.database,
                    handlers = listOf(
                        OppfolgingsplanCreatedOutboxHandler(
                            publisher = publisher,
                            oppfolgingsplanUrl = OPPFOLGINGSPLAN_URL,
                        ),
                    ),
                    reconcilers = listOf(LegacyOppfolgingsplanOutboxReconciler()),
                )

                processor.processReadyMessages() shouldBe OutboxBatchResult(sent = 1)

                val reconciled = TestDB.database.findTestOutboxMessage(planUuid)
                reconciled?.uuid shouldBe eventId
                reconciled?.status shouldBe OutboxStatus.SENT
                coVerify(exactly = 1) { publisher.publish(any()) }
            }

            it("does not reconcile plans that already have an outbox message") {
                val planUuid = TestDB.database.persistOppfolgingsplan(defaultPersistedOppfolgingsplan())
                val eventId = TestDB.database.findEventId(planUuid).shouldNotBeNull()
                TestDB.database.insertTestOutboxMessage(eventId, planUuid)

                val reconciled = TestDB.database.exposedTransaction {
                    LegacyOppfolgingsplanOutboxReconciler().reconcile(this)
                }

                reconciled shouldBeExactly 0
            }

            it("does not reconcile plans outside the rolling-deployment window") {
                val now = Instant.parse("2026-08-11T10:00:00Z")
                val oldPlan = defaultPersistedOppfolgingsplan().copy(
                    createdAt = now.minus(Duration.ofDays(2)),
                )
                val planUuid = TestDB.database.persistOppfolgingsplan(oldPlan)

                val reconciled = TestDB.database.exposedTransaction {
                    LegacyOppfolgingsplanOutboxReconciler(
                        clock = Clock.fixed(now, ZoneOffset.UTC),
                        lookback = Duration.ofDays(1),
                    ).reconcile(this)
                }

                reconciled shouldBeExactly 0
                TestDB.database.findTestOutboxMessage(planUuid).shouldBeNull()
            }

            it("does not let two processors claim the same message") {
                val externalRef = UUID.randomUUID()
                TestDB.database.insertTestOutboxMessage(UUID.randomUUID(), externalRef)
                val claimed = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val processCount = AtomicInteger()
                val handler = object : OutboxMessageHandler {
                    override val messageType = OutboxMessageType.OPPFOLGINGSPLAN_OPPRETTET

                    override suspend fun process(
                        transaction: JdbcTransaction,
                        message: OutboxMessage,
                    ): OutboxResult {
                        processCount.incrementAndGet()
                        claimed.complete(Unit)
                        release.await()
                        return OutboxResult.SENT
                    }
                }
                val firstProcessor = OutboxProcessor(TestDB.database, listOf(handler))
                val secondProcessor = OutboxProcessor(TestDB.database, listOf(handler))

                coroutineScope {
                    val first = async { firstProcessor.processReadyMessages() }
                    claimed.await()
                    try {
                        secondProcessor.processReadyMessages().processed shouldBeExactly 0
                    } finally {
                        release.complete(Unit)
                    }
                    first.await() shouldBe OutboxBatchResult(sent = 1)
                }
                processCount.get() shouldBeExactly 1
            }
        }

        it("does not include payload or references in toString") {
            val message = OutboxMessage(
                uuid = UUID.randomUUID(),
                messageType = OutboxMessageType.OPPFOLGINGSPLAN_OPPRETTET,
                dedupKey = "sensitive-dedup-key",
                externalRef = "sensitive-external-ref",
                payload = """{"fnr":"12345678901"}""",
                scheduledAt = Instant.now(),
                status = OutboxStatus.READY,
                attemptCount = 0,
                lastAttemptAt = null,
                sentAt = null,
                createdAt = Instant.now(),
            )

            message.toString() shouldNotContain "sensitive-dedup-key"
            message.toString() shouldNotContain "sensitive-external-ref"
            message.toString() shouldNotContain "12345678901"
        }
    })

private const val OPPFOLGINGSPLAN_URL = "https://www.ekstern.dev.nav.no/syk/oppfolgingsplan/sykmeldt"

private suspend fun DatabaseInterface.insertTestOutboxMessage(
    messageUuid: UUID,
    externalRef: UUID,
    scheduledAt: Instant = Instant.now(),
) = exposedTransaction {
    insertOutboxMessage(
        uuid = messageUuid,
        messageType = OutboxMessageType.OPPFOLGINGSPLAN_OPPRETTET,
        dedupKey = externalRef.toString(),
        externalRef = externalRef.toString(),
        payload = "{}",
        scheduledAt = scheduledAt,
    )
}

private suspend fun DatabaseInterface.findEventId(
    oppfolgingsplanUuid: UUID,
): UUID? = exposedTransaction(readOnly = true) {
    OppfolgingsplanOutboxTable
        .select(OppfolgingsplanOutboxTable.eventId)
        .where { OppfolgingsplanOutboxTable.uuid eq oppfolgingsplanUuid }
        .singleOrNull()
        ?.get(OppfolgingsplanOutboxTable.eventId)
}

private suspend fun DatabaseInterface.findTestOutboxMessage(
    externalRef: UUID,
): OutboxMessage? = findOutboxMessage(
    messageType = OutboxMessageType.OPPFOLGINGSPLAN_OPPRETTET,
    dedupKey = externalRef.toString(),
)
