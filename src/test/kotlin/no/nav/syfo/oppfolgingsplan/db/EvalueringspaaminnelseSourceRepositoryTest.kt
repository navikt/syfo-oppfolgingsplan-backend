package no.nav.syfo.oppfolgingsplan.db

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.syfo.TestDB
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.defaultPersistedOppfolgingsplan
import no.nav.syfo.persistOppfolgingsplan
import no.nav.syfo.sykmelding.db.SykmeldingsperiodeRepository
import no.nav.syfo.sykmelding.db.domain.SykmeldingsperiodeToStore
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

private const val NARMESTE_LEDER_ID = "narmeste-leder-id"

class EvalueringspaaminnelseSourceRepositoryTest :
    DescribeSpec({
        val sykmeldingsperiodeRepository = SykmeldingsperiodeRepository(TestDB.database)
        val repository = EvalueringspaaminnelseSourceRepository(TestDB.database)
        val oslo = ZoneId.of("Europe/Oslo")
        val today = LocalDate.of(2026, 5, 20)
        val todayClock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        val sykmeldtFnr = "12345678901"
        val organisasjonsnummer = "999999999"

        beforeTest {
            TestDB.clearAllData()
        }

        describe("findSource") {
            it("returns eligible source data when a matching active sykmeldingsperiode exists") {
                val planUuid = TestDB.database.persistPlanForSourceLookup(
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnummer = organisasjonsnummer,
                )
                sykmeldingsperiodeRepository.storePeriod(
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnummer = organisasjonsnummer,
                    sykmeldingId = "active-sykmelding",
                    fom = today.minusDays(2),
                    tom = today.plusDays(2),
                )

                val source = repository.findSource(planUuid, clock = todayClock)

                source shouldBe EvalueringspaaminnelseSource.Eligible(
                    EvalueringspaaminnelseSourceData(
                        sykmeldtFnr = sykmeldtFnr,
                        narmesteLederId = NARMESTE_LEDER_ID,
                        organisasjonsnummer = organisasjonsnummer,
                    ),
                )
                source.toString().contains(sykmeldtFnr) shouldBe false
                source.toString().contains(NARMESTE_LEDER_ID) shouldBe false
                source.toString().contains(organisasjonsnummer) shouldBe false
            }

            it("returns no longer eligible when all matching sykmeldingsperioder are expired") {
                val planUuid = TestDB.database.persistPlanForSourceLookup(
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnummer = organisasjonsnummer,
                )
                sykmeldingsperiodeRepository.storePeriod(
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnummer = organisasjonsnummer,
                    sykmeldingId = "expired-sykmelding",
                    fom = today.minusDays(10),
                    tom = today.minusDays(1),
                )

                repository.findSource(planUuid, clock = todayClock) shouldBe
                    EvalueringspaaminnelseSource.NoLongerEligible
            }

            it("returns no longer eligible when matching sykmeldingsperioder start in the future") {
                val planUuid = TestDB.database.persistPlanForSourceLookup(
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnummer = organisasjonsnummer,
                )
                sykmeldingsperiodeRepository.storePeriod(
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnummer = organisasjonsnummer,
                    sykmeldingId = "future-sykmelding",
                    fom = today.plusDays(1),
                    tom = today.plusDays(10),
                )

                repository.findSource(planUuid, clock = todayClock) shouldBe
                    EvalueringspaaminnelseSource.NoLongerEligible
            }

            it("returns no longer eligible when matching sykmeldingsperioder are invalidated") {
                val planUuid = TestDB.database.persistPlanForSourceLookup(
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnummer = organisasjonsnummer,
                )
                sykmeldingsperiodeRepository.storePeriod(
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnummer = organisasjonsnummer,
                    sykmeldingId = "invalidated-sykmelding",
                    fom = today.minusDays(2),
                    tom = today.plusDays(2),
                )
                sykmeldingsperiodeRepository.invalidateSykmelding("invalidated-sykmelding")

                repository.findSource(planUuid, clock = todayClock) shouldBe
                    EvalueringspaaminnelseSource.NoLongerEligible
            }

            it("returns not found when the source oppfolgingsplan does not exist") {
                repository.findSource(UUID.randomUUID(), clock = todayClock) shouldBe
                    EvalueringspaaminnelseSource.NotFound
            }

            it("uses Europe/Oslo date when UTC and Oslo are on different calendar dates") {
                val boundaryInstant = Instant.parse("2026-05-20T22:30:00Z")
                val boundaryClock = Clock.fixed(boundaryInstant, ZoneOffset.UTC)
                val osloToday = LocalDate.ofInstant(boundaryInstant, oslo)
                val planUuid = TestDB.database.persistPlanForSourceLookup(
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnummer = organisasjonsnummer,
                )
                sykmeldingsperiodeRepository.storePeriod(
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnummer = organisasjonsnummer,
                    sykmeldingId = "oslo-boundary-sykmelding",
                    fom = osloToday,
                    tom = osloToday,
                )

                repository.findSource(planUuid, clock = boundaryClock) shouldBe
                    EvalueringspaaminnelseSource.Eligible(
                        EvalueringspaaminnelseSourceData(
                            sykmeldtFnr = sykmeldtFnr,
                            narmesteLederId = NARMESTE_LEDER_ID,
                            organisasjonsnummer = organisasjonsnummer,
                        ),
                    )
            }
        }
    })

private fun DatabaseInterface.persistPlanForSourceLookup(
    sykmeldtFnr: String,
    organisasjonsnummer: String,
): UUID = persistOppfolgingsplan(
    defaultPersistedOppfolgingsplan().copy(
        sykmeldtFnr = sykmeldtFnr,
        narmesteLederId = NARMESTE_LEDER_ID,
        organisasjonsnummer = organisasjonsnummer,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    ),
)

private fun SykmeldingsperiodeRepository.storePeriod(
    sykmeldtFnr: String,
    organisasjonsnummer: String,
    sykmeldingId: String,
    fom: LocalDate,
    tom: LocalDate,
) {
    storeSykmeldingsperioder(
        listOf(
            SykmeldingsperiodeToStore(
                sykmeldtFnr = sykmeldtFnr,
                organisasjonsnummer = organisasjonsnummer,
                sykmeldingId = sykmeldingId,
                fom = fom,
                tom = tom,
            ),
        ),
    )
}
