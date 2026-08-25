package no.nav.syfo.oppfolgingsplan.outbox

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.TestDB
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.outbox.OutboxWorker
import no.nav.syfo.application.outbox.db.findOutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxCancellationReason
import no.nav.syfo.application.outbox.domain.OutboxStatus
import no.nav.syfo.defaultPersistedOppfolgingsplan
import no.nav.syfo.defaultSykmeldt
import no.nav.syfo.oppfolgingsplan.db.findPaaminnelseBy
import no.nav.syfo.oppfolgingsplan.db.upsertPaaminnelse
import no.nav.syfo.oppfolgingsplan.service.PAAMINNELSE_ETTER_DAGER
import no.nav.syfo.oppfolgingsplan.service.PaaminnelseService
import no.nav.syfo.persistOppfolgingsplan
import no.nav.syfo.sykmelding.db.SykmeldingsperiodeRepository
import no.nav.syfo.sykmelding.db.domain.SykmeldingsperiodeToStore
import no.nav.syfo.varsel.budstikka.infrastructure.BudstikkaPublisher
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class PaaminnelseOutboxIntegrationTest :
    DescribeSpec({
        val zone = ZoneId.of("Europe/Oslo")
        val narmestelederId = defaultSykmeldt().narmestelederId
        val orderedAt = Instant.parse("2025-06-19T10:00:00Z")
        val sykmeldingsperiodeFom = LocalDate.of(2025, 6, 1)
        val availableAt = sykmeldingsperiodeFom
            .plusDays(PAAMINNELSE_ETTER_DAGER)
            .atStartOfDay(zone)
            .toInstant()
        val repository = SykmeldingsperiodeRepository(TestDB.database)
        val service = PaaminnelseService(
            database = TestDB.database,
            sykmeldingsperiodeRepository = repository,
            clock = Clock.fixed(orderedAt, zone),
        )

        beforeTest {
            TestDB.clearAllData()
            repository.storeSykmeldingsperioder(
                listOf(
                    SykmeldingsperiodeToStore(
                        sykmeldtFnr = defaultSykmeldt().fnr,
                        organisasjonsnummer = defaultSykmeldt().orgnummer,
                        sykmeldingId = "sykmelding",
                        fom = sykmeldingsperiodeFom,
                        tom = sykmeldingsperiodeFom.plusDays(30),
                    ),
                ),
            )
        }

        it("creates a scheduled outbox command atomically when a reminder is ordered") {
            service.activatePaaminnelse(defaultSykmeldt())

            val paaminnelse = TestDB.database.findPaaminnelseBy(
                defaultSykmeldt().fnr,
                defaultSykmeldt().orgnummer,
            ).shouldNotBeNull()
            val message = TestDB.database.findOutboxMessage(
                OppfolgingsplanOutboxMessageType.PAAMINNELSE,
                "${paaminnelse.uuid}:${paaminnelse.sykmeldingsperiodeId}",
            ).shouldNotBeNull()
            val dineSykmeldteMessage = TestDB.database.findOutboxMessage(
                OppfolgingsplanOutboxMessageType.PAAMINNELSE_DINE_SYKMELDTE,
                "${paaminnelse.uuid}:${paaminnelse.sykmeldingsperiodeId}",
            ).shouldNotBeNull()

            message.externalRef shouldBe paaminnelse.uuid.toString()
            message.availableAt shouldBe availableAt
            message.status shouldBe OutboxStatus.READY
            message.payload.contains(defaultSykmeldt().fnr) shouldBe false
            message.payload.contains(defaultSykmeldt().orgnummer) shouldBe false
            message.payload.contains(narmestelederId) shouldBe true
            dineSykmeldteMessage.externalRef shouldBe paaminnelse.uuid.toString()
            dineSykmeldteMessage.availableAt shouldBe availableAt
            dineSykmeldteMessage.status shouldBe OutboxStatus.READY
        }

        it("keeps the scheduled outbox command when a reminder is ordered again") {
            service.activatePaaminnelse(defaultSykmeldt())
            val paaminnelse = TestDB.database.findPaaminnelseBy(
                defaultSykmeldt().fnr,
                defaultSykmeldt().orgnummer,
            ).shouldNotBeNull()
            val firstMessage = TestDB.database.findOutboxMessage(
                OppfolgingsplanOutboxMessageType.PAAMINNELSE,
                "${paaminnelse.uuid}:${paaminnelse.sykmeldingsperiodeId}",
            ).shouldNotBeNull()

            service.activatePaaminnelse(defaultSykmeldt())

            TestDB.database.findOutboxMessage(
                OppfolgingsplanOutboxMessageType.PAAMINNELSE,
                "${paaminnelse.uuid}:${paaminnelse.sykmeldingsperiodeId}",
            ) shouldBe firstMessage
            TestDB.database.findOutboxMessage(
                OppfolgingsplanOutboxMessageType.PAAMINNELSE_DINE_SYKMELDTE,
                "${paaminnelse.uuid}:${paaminnelse.sykmeldingsperiodeId}",
            ).shouldNotBeNull()
        }

        it("cancels a reminder that has been deactivated before delivery") {
            service.activatePaaminnelse(defaultSykmeldt())
            service.deactivatePaaminnelse(defaultSykmeldt())
            val paaminnelse = TestDB.database.findPaaminnelseBy(
                defaultSykmeldt().fnr,
                defaultSykmeldt().orgnummer,
            ).shouldNotBeNull()
            val worker = OutboxWorker(
                database = TestDB.database,
                handlers = listOf(PaaminnelseOutboxHandler(TestDB.database, service, mockk(relaxed = true))),
                clock = Clock.fixed(availableAt, zone),
            )

            worker.runOnce().cancelled shouldBe 1

            TestDB.database.findOutboxMessage(
                OppfolgingsplanOutboxMessageType.PAAMINNELSE,
                "${paaminnelse.uuid}:${paaminnelse.sykmeldingsperiodeId}",
            ).shouldNotBeNull().cancellationReason shouldBe OutboxCancellationReason.NO_LONGER_REQUESTED
        }

        it("cancels a reminder when its source has been deleted before delivery") {
            service.activatePaaminnelse(defaultSykmeldt())
            val paaminnelse = TestDB.database.findPaaminnelseBy(
                defaultSykmeldt().fnr,
                defaultSykmeldt().orgnummer,
            ).shouldNotBeNull()
            TestDB.database.deletePaaminnelse(paaminnelse.uuid) shouldBe 1
            val publisher = mockk<BudstikkaPublisher>(relaxed = true)
            val worker = OutboxWorker(
                database = TestDB.database,
                handlers = listOf(PaaminnelseOutboxHandler(TestDB.database, service, publisher)),
                clock = Clock.fixed(availableAt, zone),
            )

            worker.runOnce().cancelled shouldBe 1

            TestDB.database.findOutboxMessage(
                OppfolgingsplanOutboxMessageType.PAAMINNELSE,
                "${paaminnelse.uuid}:${paaminnelse.sykmeldingsperiodeId}",
            ).shouldNotBeNull().cancellationReason shouldBe OutboxCancellationReason.SOURCE_NOT_FOUND
            coVerify(exactly = 0) { publisher.publishPaaminnelse(any(), any(), any(), any(), any()) }
        }

        it("cancels a reminder when its sykmeldingsperiode has changed before delivery") {
            service.activatePaaminnelse(defaultSykmeldt())
            val paaminnelse = TestDB.database.findPaaminnelseBy(
                defaultSykmeldt().fnr,
                defaultSykmeldt().orgnummer,
            ).shouldNotBeNull()
            repository.storeSykmeldingsperioder(
                listOf(
                    SykmeldingsperiodeToStore(
                        sykmeldtFnr = defaultSykmeldt().fnr,
                        organisasjonsnummer = defaultSykmeldt().orgnummer,
                        sykmeldingId = "changed-sykmelding",
                        fom = sykmeldingsperiodeFom,
                        tom = sykmeldingsperiodeFom.plusDays(30),
                    ),
                ),
            )
            TestDB.database.upsertPaaminnelse(
                sykmeldt = defaultSykmeldt(),
                bestilt = true,
                sykmeldingsperiodeId = repository.findBySykmeldingId("changed-sykmelding").single().id,
            )
            val publisher = mockk<BudstikkaPublisher>(relaxed = true)
            val worker = OutboxWorker(
                database = TestDB.database,
                handlers = listOf(PaaminnelseOutboxHandler(TestDB.database, service, publisher)),
                clock = Clock.fixed(availableAt, zone),
            )

            worker.runOnce().cancelled shouldBe 1

            TestDB.database.findOutboxMessage(
                OppfolgingsplanOutboxMessageType.PAAMINNELSE,
                "${paaminnelse.uuid}:${paaminnelse.sykmeldingsperiodeId}",
            ).shouldNotBeNull().cancellationReason shouldBe OutboxCancellationReason.SUPERSEDED
            coVerify(exactly = 0) { publisher.publishPaaminnelse(any(), any(), any(), any(), any()) }
        }

        it("cancels a reminder when an oppfolgingsplan has been created before delivery") {
            service.activatePaaminnelse(defaultSykmeldt())
            val paaminnelse = TestDB.database.findPaaminnelseBy(
                defaultSykmeldt().fnr,
                defaultSykmeldt().orgnummer,
            ).shouldNotBeNull()
            TestDB.database.persistOppfolgingsplan(
                defaultPersistedOppfolgingsplan().copy(createdAt = availableAt),
            )
            val publisher = mockk<BudstikkaPublisher>(relaxed = true)
            val worker = OutboxWorker(
                database = TestDB.database,
                handlers = listOf(PaaminnelseOutboxHandler(TestDB.database, service, publisher)),
                clock = Clock.fixed(availableAt, zone),
            )

            worker.runOnce().cancelled shouldBe 1

            TestDB.database.findOutboxMessage(
                OppfolgingsplanOutboxMessageType.PAAMINNELSE,
                "${paaminnelse.uuid}:${paaminnelse.sykmeldingsperiodeId}",
            ).shouldNotBeNull().cancellationReason shouldBe OutboxCancellationReason.SOURCE_NO_LONGER_ELIGIBLE
            coVerify(exactly = 0) { publisher.publishPaaminnelse(any(), any(), any(), any(), any()) }
        }

        it("cancels a reminder when its sykmeldingsperiode is no longer active") {
            service.activatePaaminnelse(defaultSykmeldt())
            val paaminnelse = TestDB.database.findPaaminnelseBy(
                defaultSykmeldt().fnr,
                defaultSykmeldt().orgnummer,
            ).shouldNotBeNull()
            repository.invalidateSykmelding("sykmelding")
            val publisher = mockk<BudstikkaPublisher>(relaxed = true)
            val worker = OutboxWorker(
                database = TestDB.database,
                handlers = listOf(PaaminnelseOutboxHandler(TestDB.database, service, publisher)),
                clock = Clock.fixed(availableAt, zone),
            )

            worker.runOnce().cancelled shouldBe 1

            TestDB.database.findOutboxMessage(
                OppfolgingsplanOutboxMessageType.PAAMINNELSE,
                "${paaminnelse.uuid}:${paaminnelse.sykmeldingsperiodeId}",
            ).shouldNotBeNull().cancellationReason shouldBe OutboxCancellationReason.SOURCE_NO_LONGER_ELIGIBLE
            coVerify(exactly = 0) { publisher.publishPaaminnelse(any(), any(), any(), any(), any()) }
        }

        it("publishes an active reminder to Budstikka with the outbox event ID") {
            service.activatePaaminnelse(defaultSykmeldt())
            val paaminnelse = TestDB.database.findPaaminnelseBy(
                defaultSykmeldt().fnr,
                defaultSykmeldt().orgnummer,
            ).shouldNotBeNull()
            val message = TestDB.database.findOutboxMessage(
                OppfolgingsplanOutboxMessageType.PAAMINNELSE,
                "${paaminnelse.uuid}:${paaminnelse.sykmeldingsperiodeId}",
            ).shouldNotBeNull()
            val publisher = mockk<BudstikkaPublisher>()
            coEvery { publisher.publishPaaminnelse(any(), any(), any(), any(), any()) } returns Unit
            val worker = OutboxWorker(
                database = TestDB.database,
                handlers = listOf(PaaminnelseOutboxHandler(TestDB.database, service, publisher)),
                clock = Clock.fixed(availableAt, zone),
            )

            worker.runOnce().sent shouldBe 1

            coVerify(exactly = 1) {
                publisher.publishPaaminnelse(
                    paaminnelseUuid = paaminnelse.uuid,
                    eventId = message.uuid,
                    sykmeldtFnr = defaultSykmeldt().fnr,
                    orgnummer = defaultSykmeldt().orgnummer,
                    narmestelederId = narmestelederId,
                )
            }
        }

        it("publishes an active reminder to Dine Sykmeldte with its own outbox event ID") {
            service.activatePaaminnelse(defaultSykmeldt())
            val paaminnelse = TestDB.database.findPaaminnelseBy(
                defaultSykmeldt().fnr,
                defaultSykmeldt().orgnummer,
            ).shouldNotBeNull()
            val message = TestDB.database.findOutboxMessage(
                OppfolgingsplanOutboxMessageType.PAAMINNELSE_DINE_SYKMELDTE,
                "${paaminnelse.uuid}:${paaminnelse.sykmeldingsperiodeId}",
            ).shouldNotBeNull()
            val publisher = mockk<BudstikkaPublisher>()
            coEvery { publisher.publishPaaminnelseToDineSykmeldte(any(), any(), any(), any(), any()) } returns Unit
            val worker = OutboxWorker(
                database = TestDB.database,
                handlers = listOf(PaaminnelseDineSykmeldteOutboxHandler(TestDB.database, service, publisher)),
                clock = Clock.fixed(availableAt, zone),
            )

            worker.runOnce().sent shouldBe 1

            coVerify(exactly = 1) {
                publisher.publishPaaminnelseToDineSykmeldte(
                    paaminnelseUuid = paaminnelse.uuid,
                    eventId = message.uuid,
                    sykmeldtFnr = defaultSykmeldt().fnr,
                    orgnummer = defaultSykmeldt().orgnummer,
                    narmestelederId = narmestelederId,
                )
            }
        }
    })

private fun DatabaseInterface.deletePaaminnelse(paaminnelseUuid: UUID): Int = connection.use { connection ->
    connection.prepareStatement("DELETE FROM paaminnelse WHERE uuid = ?").use { statement ->
        statement.setObject(1, paaminnelseUuid)
        statement.executeUpdate()
    }.also {
        connection.commit()
    }
}
