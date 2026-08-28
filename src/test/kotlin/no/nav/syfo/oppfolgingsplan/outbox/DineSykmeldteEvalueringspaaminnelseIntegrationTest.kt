package no.nav.syfo.oppfolgingsplan.outbox

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.TestDB
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.outbox.MutableClock
import no.nav.syfo.application.outbox.OutboxWorker
import no.nav.syfo.application.outbox.db.findOutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxCancellationReason
import no.nav.syfo.application.outbox.domain.OutboxStatus
import no.nav.syfo.defaultOppfolgingsplan
import no.nav.syfo.defaultSykmeldt
import no.nav.syfo.dinesykmeldte.client.DineSykmeldteSykmelding
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.oppfolgingsplan.db.EvalueringspaaminnelseSourceRepository
import no.nav.syfo.oppfolgingsplan.db.OppfolgingsplanFinalizationCommand
import no.nav.syfo.oppfolgingsplan.db.OppfolgingsplanFinalizationRepository
import no.nav.syfo.oppfolgingsplan.service.EvalueringspaaminnelseEligibilityService
import no.nav.syfo.sykmelding.db.SykmeldingsperiodeRepository
import no.nav.syfo.sykmelding.db.domain.SykmeldingsperiodeToStore
import no.nav.syfo.varsel.budstikka.infrastructure.BudstikkaPublisher
import java.sql.SQLException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.TimeoutException

class DineSykmeldteEvalueringspaaminnelseIntegrationTest :
    DescribeSpec({
        val sendInstant = Instant.parse("2030-05-17T07:00:00Z")
        val evalueringsdato = LocalDate.of(2030, 5, 20)
        val sykmeldt = defaultSykmeldt().copy(
            fnr = "00000000000",
            navn = "Kari Normann",
            orgnummer = "999999999",
            sykmeldinger = listOf(DineSykmeldteSykmelding("ARNESEN, HOLM OG BAKKEN")),
        )
        val repository = EvalueringspaaminnelseSourceRepository(TestDB.database)
        val eligibilityService = EvalueringspaaminnelseEligibilityService(repository)
        val sykmeldingsperiodeRepository = SykmeldingsperiodeRepository(TestDB.database)

        beforeTest {
            TestDB.clearAllData()
        }

        it("publishes an eligible reminder once and marks the Dine Sykmeldte row sent") {
            val planUuid = TestDB.database.persistReminder(sykmeldt, evalueringsdato)
            sykmeldingsperiodeRepository.storeActivePeriod(sykmeldt)
            val message = TestDB.database.findOutboxMessage(
                OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_DINE_SYKMELDTE,
                planUuid.toString(),
            ).shouldNotBeNull()
            val publisher = mockk<BudstikkaPublisher>()
            coEvery {
                publisher.publishDineSykmeldteEvalueringspaaminnelse(any(), any(), any(), any())
            } returns Unit
            val worker = dineSykmeldteWorker(eligibilityService, publisher, Clock.fixed(sendInstant, ZoneOffset.UTC))

            worker.runOnce().sent shouldBe 1

            coVerify(exactly = 1) {
                publisher.publishDineSykmeldteEvalueringspaaminnelse(
                    oppfolgingsplanUuid = planUuid,
                    sykmeldtFnr = sykmeldt.fnr,
                    organisasjonsnummer = sykmeldt.orgnummer,
                    eventId = message.uuid,
                )
            }
            TestDB.database.findOutboxMessage(
                OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_DINE_SYKMELDTE,
                planUuid.toString(),
            ).shouldNotBeNull().apply {
                status shouldBe OutboxStatus.SENT
                completedAt.shouldNotBeNull()
            }
        }

        it("cancels the reminder when the source has no active sykmeldingsperiode") {
            val planUuid = TestDB.database.persistReminder(sykmeldt, evalueringsdato)
            val publisher = mockk<BudstikkaPublisher>(relaxed = true)
            val worker = dineSykmeldteWorker(eligibilityService, publisher, Clock.fixed(sendInstant, ZoneOffset.UTC))

            worker.runOnce().cancelled shouldBe 1

            coVerify(exactly = 0) {
                publisher.publishDineSykmeldteEvalueringspaaminnelse(any(), any(), any(), any())
            }
            TestDB.database.findOutboxMessage(
                OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_DINE_SYKMELDTE,
                planUuid.toString(),
            ).shouldNotBeNull().apply {
                status shouldBe OutboxStatus.CANCELLED
                cancellationReason shouldBe OutboxCancellationReason.SOURCE_NO_LONGER_ELIGIBLE
                completedAt.shouldNotBeNull()
            }
        }

        it("cancels the reminder terminally when the source plan is missing") {
            val planUuid = TestDB.database.persistReminder(sykmeldt, evalueringsdato)
            TestDB.database.deletePlan(planUuid)
            val publisher = mockk<BudstikkaPublisher>(relaxed = true)
            val worker = dineSykmeldteWorker(eligibilityService, publisher, Clock.fixed(sendInstant, ZoneOffset.UTC))

            worker.runOnce().cancelled shouldBe 1

            coVerify(exactly = 0) {
                publisher.publishDineSykmeldteEvalueringspaaminnelse(any(), any(), any(), any())
            }
            TestDB.database.findOutboxMessage(
                OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_DINE_SYKMELDTE,
                planUuid.toString(),
            ).shouldNotBeNull().apply {
                status shouldBe OutboxStatus.CANCELLED
                cancellationReason shouldBe OutboxCancellationReason.SOURCE_NOT_FOUND
                completedAt.shouldNotBeNull()
            }
        }

        it("retries a failed Budstikka publish with the same outbox EventId before marking it sent") {
            val planUuid = TestDB.database.persistReminder(sykmeldt, evalueringsdato)
            sykmeldingsperiodeRepository.storeActivePeriod(sykmeldt)
            val message = TestDB.database.findOutboxMessage(
                OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_DINE_SYKMELDTE,
                planUuid.toString(),
            ).shouldNotBeNull()
            val observedEventIds = mutableListOf<UUID>()
            var publishAttempt = 0
            val publisher = mockk<BudstikkaPublisher>()
            coEvery {
                publisher.publishDineSykmeldteEvalueringspaaminnelse(
                    any(),
                    any(),
                    any(),
                    capture(observedEventIds),
                )
            } coAnswers {
                publishAttempt++
                if (publishAttempt == 1) throw TimeoutException("Forced Budstikka timeout")
            }
            val clock = MutableClock(sendInstant)
            val worker = dineSykmeldteWorker(eligibilityService, publisher, clock)

            worker.runOnce().retryScheduled shouldBe 1
            val retrying = TestDB.database.findOutboxMessage(
                OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_DINE_SYKMELDTE,
                planUuid.toString(),
            ).shouldNotBeNull()
            retrying.status shouldBe OutboxStatus.READY
            retrying.failureCount shouldBe 1
            retrying.lastFailureAt.shouldNotBeNull()
            retrying.completedAt shouldBe null

            clock.advance(Duration.between(clock.instant(), retrying.availableAt).plusMillis(1))
            worker.runOnce().sent shouldBe 1

            observedEventIds shouldBe listOf(message.uuid, message.uuid)
            TestDB.database.findOutboxMessage(
                OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_DINE_SYKMELDTE,
                planUuid.toString(),
            ).shouldNotBeNull().status shouldBe OutboxStatus.SENT
        }

        it("schedules retry when the source database lookup fails") {
            val planUuid = TestDB.database.persistReminder(sykmeldt, evalueringsdato)
            val failingRepository = mockk<EvalueringspaaminnelseSourceRepository>()
            coEvery {
                failingRepository.findSourceFacts(planUuid, any())
            } throws SQLException("Forced database failure")
            val publisher = mockk<BudstikkaPublisher>(relaxed = true)
            val worker = dineSykmeldteWorker(
                EvalueringspaaminnelseEligibilityService(failingRepository),
                publisher,
                Clock.fixed(sendInstant, ZoneOffset.UTC),
            )

            worker.runOnce().retryScheduled shouldBe 1

            coVerify(exactly = 0) {
                publisher.publishDineSykmeldteEvalueringspaaminnelse(any(), any(), any(), any())
            }
            TestDB.database.findOutboxMessage(
                OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_DINE_SYKMELDTE,
                planUuid.toString(),
            ).shouldNotBeNull().apply {
                status shouldBe OutboxStatus.READY
                failureCount shouldBe 1
                lastFailureAt.shouldNotBeNull()
                completedAt shouldBe null
            }
        }
    })

private suspend fun DatabaseInterface.persistReminder(
    sykmeldt: Sykmeldt,
    evalueringsdato: LocalDate,
): UUID = OppfolgingsplanFinalizationRepository(this).finalize(
    OppfolgingsplanFinalizationCommand(
        narmesteLederFnr = "11111111111",
        sykmeldt = sykmeldt,
        createOppfolgingsplanRequest = defaultOppfolgingsplan().copy(
            evalueringPaaminnelse = true,
            evalueringsdato = evalueringsdato,
        ),
        stillingstittel = "Systemutvikler",
        stillingsprosent = null,
        reminderDefinitions = EvalueringPaaminnelseFactory.create(
            enabled = true,
            evalueringsdato = evalueringsdato,
        ),
    ),
).oppfolgingsplanUuid

private fun SykmeldingsperiodeRepository.storeActivePeriod(sykmeldt: Sykmeldt) {
    storeSykmeldingsperioder(
        listOf(
            SykmeldingsperiodeToStore(
                sykmeldtFnr = sykmeldt.fnr,
                organisasjonsnummer = sykmeldt.orgnummer,
                sykmeldingId = "active-sykmelding",
                fom = LocalDate.of(2030, 5, 1),
                tom = LocalDate.of(2030, 5, 31),
            ),
        ),
    )
}

private fun dineSykmeldteWorker(
    eligibilityService: EvalueringspaaminnelseEligibilityService,
    publisher: BudstikkaPublisher,
    clock: Clock,
) = OutboxWorker(
    database = TestDB.database,
    handlers = listOf(
        DineSykmeldteEvalueringspaaminnelseHandler(eligibilityService, publisher),
    ),
    clock = clock,
)

private fun DatabaseInterface.deletePlan(planUuid: UUID) = connection.use { connection ->
    connection.prepareStatement("DELETE FROM oppfolgingsplan WHERE uuid = ?").use {
        it.setObject(1, planUuid)
        it.executeUpdate()
    }
    connection.commit()
}
