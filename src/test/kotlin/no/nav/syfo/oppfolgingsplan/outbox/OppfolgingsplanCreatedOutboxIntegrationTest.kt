package no.nav.syfo.oppfolgingsplan.outbox

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.TestDB
import no.nav.syfo.aareg.AaregService
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.outbox.OutboxWorker
import no.nav.syfo.application.outbox.db.findOutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxCancellationReason
import no.nav.syfo.application.outbox.domain.OutboxStatus
import no.nav.syfo.defaultOppfolgingsplan
import no.nav.syfo.defaultPersistedOppfolgingsplanUtkast
import no.nav.syfo.defaultSykmeldt
import no.nav.syfo.findEventId
import no.nav.syfo.findOppfolgingsplanUtkastByNarmesteLederId
import no.nav.syfo.oppfolgingsplan.db.OppfolgingsplanFinalizationRepository
import no.nav.syfo.oppfolgingsplan.db.findAllOppfolgingsplanerBy
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanBy
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.oppfolgingsplan.service.UnntaksvurderingService
import no.nav.syfo.persistOppfolgingsplanUtkast
import no.nav.syfo.pdl.PdlService
import no.nav.syfo.varsel.EsyfovarselProducer
import no.nav.syfo.varsel.budstikka.infrastructure.BudstikkaPublisher
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class OppfolgingsplanCreatedOutboxIntegrationTest :
    DescribeSpec({
        val now = Instant.parse("2030-08-13T12:00:00Z")
        val clock = Clock.fixed(now, ZoneOffset.UTC)

        beforeTest {
            TestDB.clearAllData()
            clearAllMocks(currentThreadOnly = true)
        }

        describe("atomic creation") {
            it("persists the plan and its immutable outbox command together") {
                val planUuid = TestDB.database.createOppfolgingsplan()
                val plan = TestDB.database.findOppfolgingsplanBy(planUuid).shouldNotBeNull()

                val message = TestDB.database.findOutboxMessage(
                    OppfolgingsplanOutboxMessageType.CREATED,
                    planUuid.toString(),
                ).shouldNotBeNull()
                message.uuid shouldBe TestDB.database.findEventId(planUuid)
                message.externalRef shouldBe planUuid.toString()
                message.availableAt shouldBe plan.createdAt
                message.status shouldBe OutboxStatus.READY
            }

            it("rolls back the plan and draft deletion when outbox persistence fails") {
                val draft = defaultPersistedOppfolgingsplanUtkast()
                TestDB.database.persistOppfolgingsplanUtkast(draft)
                TestDB.database.rejectCreatedOutboxInserts()

                try {
                    shouldThrow<Exception> {
                        TestDB.database.createOppfolgingsplan(narmesteLederId = draft.narmesteLederId)
                    }
                } finally {
                    TestDB.database.allowCreatedOutboxInserts()
                }

                TestDB.database.findAllOppfolgingsplanerBy(
                    defaultSykmeldt().fnr,
                    defaultSykmeldt().orgnummer,
                ).shouldBeEmpty()
                TestDB.database.findOppfolgingsplanUtkastByNarmesteLederId(draft.narmesteLederId)
                    .shouldNotBeNull()
            }
        }

        describe("delivery") {
            it("publishes with the outbox uuid and marks the command sent after acknowledgement") {
                val planUuid = TestDB.database.createOppfolgingsplan()
                val message = TestDB.database.findCreatedMessage(planUuid)
                val publisher = mockk<BudstikkaPublisher>()
                coEvery { publisher.publishOppfolgingsplanCreated(any(), any(), any()) } returns Unit
                val worker = OutboxWorker(
                    database = TestDB.database,
                    handlers = listOf(OppfolgingsplanCreatedOutboxHandler(TestDB.database, publisher)),
                    clock = clock,
                )

                worker.runOnce().sent shouldBe 1

                coVerify(exactly = 1) {
                    publisher.publishOppfolgingsplanCreated(
                        oppfolgingsplanUuid = planUuid,
                        sykmeldtFnr = defaultSykmeldt().fnr,
                        eventId = message.uuid,
                    )
                }
                TestDB.database.findCreatedMessage(planUuid).let { completedMessage ->
                    completedMessage.status shouldBe OutboxStatus.SENT
                    completedMessage.completedAt shouldBe now
                }
            }

            it("retries a technical publish failure") {
                val planUuid = TestDB.database.createOppfolgingsplan()
                val publisher = mockk<BudstikkaPublisher>()
                coEvery { publisher.publishOppfolgingsplanCreated(any(), any(), any()) } throws RuntimeException("broker down")
                val worker = OutboxWorker(
                    database = TestDB.database,
                    handlers = listOf(OppfolgingsplanCreatedOutboxHandler(TestDB.database, publisher)),
                    clock = clock,
                )

                worker.runOnce().retryScheduled shouldBe 1

                TestDB.database.findCreatedMessage(planUuid).let { message ->
                    message.status shouldBe OutboxStatus.READY
                    message.failureCount shouldBe 1
                }
            }

            it("cancels a notification when the plan is no longer eligible") {
                val planUuid = TestDB.database.createOppfolgingsplan()
                TestDB.database.setPlanHidden(planUuid, now)
                val publisher = mockk<BudstikkaPublisher>(relaxed = true)
                val worker = OutboxWorker(
                    database = TestDB.database,
                    handlers = listOf(OppfolgingsplanCreatedOutboxHandler(TestDB.database, publisher)),
                    clock = clock,
                )

                worker.runOnce().cancelled shouldBe 1

                TestDB.database.findCreatedMessage(planUuid).let { message ->
                    message.status shouldBe OutboxStatus.CANCELLED
                    message.cancellationReason shouldBe OutboxCancellationReason.SOURCE_NO_LONGER_ELIGIBLE
                }
                coVerify(exactly = 0) { publisher.publishOppfolgingsplanCreated(any(), any(), any()) }
            }

            it("cancels with source not found when the atomic source invariant is broken") {
                val planUuid = TestDB.database.createOppfolgingsplan()
                val outboxMessage = TestDB.database.findCreatedMessage(planUuid)
                TestDB.database.deletePlan(planUuid)
                val publisher = mockk<BudstikkaPublisher>(relaxed = true)
                val worker = OutboxWorker(
                    database = TestDB.database,
                    handlers = listOf(OppfolgingsplanCreatedOutboxHandler(TestDB.database, publisher)),
                    clock = clock,
                )
                val handlerLogger = LoggerFactory.getLogger(OppfolgingsplanCreatedOutboxHandler::class.java) as Logger
                val appender = ListAppender<ILoggingEvent>().apply { start() }
                handlerLogger.addAppender(appender)

                try {
                    worker.runOnce().cancelled shouldBe 1

                    TestDB.database.findCreatedMessage(planUuid).let { message ->
                        message.status shouldBe OutboxStatus.CANCELLED
                        message.cancellationReason shouldBe OutboxCancellationReason.SOURCE_NOT_FOUND
                    }
                    appender.list.any {
                        it.level == Level.ERROR &&
                            it.formattedMessage.contains(outboxMessage.uuid.toString()) &&
                            it.formattedMessage.contains(OutboxCancellationReason.SOURCE_NOT_FOUND.value)
                    } shouldBe true
                    coVerify(exactly = 0) { publisher.publishOppfolgingsplanCreated(any(), any(), any()) }
                } finally {
                    handlerLogger.detachAppender(appender)
                    appender.stop()
                }
            }

            it("cancels a notification when the plan was feilregistrert") {
                val planUuid = TestDB.database.createOppfolgingsplan()
                TestDB.database.setPlanFeilregistrert(planUuid, now)
                val publisher = mockk<BudstikkaPublisher>(relaxed = true)
                val worker = OutboxWorker(
                    database = TestDB.database,
                    handlers = listOf(OppfolgingsplanCreatedOutboxHandler(TestDB.database, publisher)),
                    clock = clock,
                )

                worker.runOnce().cancelled shouldBe 1

                TestDB.database.findCreatedMessage(planUuid).let { message ->
                    message.status shouldBe OutboxStatus.CANCELLED
                    message.cancellationReason shouldBe OutboxCancellationReason.SOURCE_NO_LONGER_ELIGIBLE
                }
                coVerify(exactly = 0) { publisher.publishOppfolgingsplanCreated(any(), any(), any()) }
            }
        }
    })

private suspend fun DatabaseInterface.createOppfolgingsplan(
    narmesteLederId: String = defaultSykmeldt().narmestelederId,
): UUID = OppfolgingsplanService(
    database = this,
    esyfovarselProducer = mockk<EsyfovarselProducer>(relaxed = true),
    pdlService = mockk<PdlService>(relaxed = true),
    aaregService = mockk<AaregService>(relaxed = true),
    unntaksvurderingService = mockk<UnntaksvurderingService>(relaxed = true),
    oppfolgingsplanFinalizationRepository = OppfolgingsplanFinalizationRepository(this),
).createOppfolgingsplan(
    narmesteLederFnr = "10987654321",
    sykmeldt = defaultSykmeldt().copy(narmestelederId = narmesteLederId),
    createOppfolgingsplanRequest = defaultOppfolgingsplan(),
)

private suspend fun DatabaseInterface.findCreatedMessage(planUuid: UUID) = findOutboxMessage(
    OppfolgingsplanOutboxMessageType.CREATED,
    planUuid.toString(),
).shouldNotBeNull()

private fun DatabaseInterface.setPlanHidden(
    planUuid: UUID,
    hiddenAt: Instant,
) = connection.use { connection ->
    connection.prepareStatement("UPDATE oppfolgingsplan SET skjult_fra = ? WHERE uuid = ?").use {
        it.setObject(1, hiddenAt.atOffset(ZoneOffset.UTC))
        it.setObject(2, planUuid)
        it.executeUpdate()
    }
    connection.commit()
}

private fun DatabaseInterface.setPlanFeilregistrert(
    planUuid: UUID,
    feilregistrertAt: Instant,
) = connection.use { connection ->
    connection.prepareStatement("UPDATE oppfolgingsplan SET feilregistrert = ? WHERE uuid = ?").use {
        it.setObject(1, feilregistrertAt.atOffset(ZoneOffset.UTC))
        it.setObject(2, planUuid)
        it.executeUpdate()
    }
    connection.commit()
}

private fun DatabaseInterface.deletePlan(planUuid: UUID) = connection.use { connection ->
    connection.prepareStatement("DELETE FROM oppfolgingsplan WHERE uuid = ?").use {
        it.setObject(1, planUuid)
        it.executeUpdate()
    }
    connection.commit()
}

private fun DatabaseInterface.rejectCreatedOutboxInserts() = connection.use { connection ->
    connection.createStatement().use { statement ->
        statement.execute(
            """
            ALTER TABLE outbox
            ADD CONSTRAINT reject_created_outbox_test
            CHECK (message_type <> 'OPPFOLGINGSPLAN_CREATED')
            """.trimIndent(),
        )
    }
    connection.commit()
}

private fun DatabaseInterface.allowCreatedOutboxInserts() = connection.use { connection ->
    connection.createStatement().use { statement ->
        statement.execute("ALTER TABLE outbox DROP CONSTRAINT reject_created_outbox_test")
    }
    connection.commit()
}
