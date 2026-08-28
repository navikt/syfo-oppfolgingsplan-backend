package no.nav.syfo.oppfolgingsplan.db

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.syfo.TestDB
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.defaultPersistedOppfolgingsplan
import no.nav.syfo.persistOppfolgingsplan
import no.nav.syfo.sykmelding.db.SykmeldingsperiodeRepository
import no.nav.syfo.sykmelding.db.domain.SykmeldingsperiodeToStore
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private const val NARMESTE_LEDER_ID = "narmeste-leder-id"

class EvalueringspaaminnelseSourceRepositoryTest :
    DescribeSpec({
        val sykmeldingsperiodeRepository = SykmeldingsperiodeRepository(TestDB.database)
        val repository = EvalueringspaaminnelseSourceRepository(TestDB.database)
        val today = LocalDate.of(2026, 5, 20)
        val sykmeldtFnr = "12345678901"
        val organisasjonsnummer = "999999999"

        beforeTest {
            TestDB.clearAllData()
        }

        describe("findSourceFacts") {
            it("returns source facts when a matching active sykmeldingsperiode exists") {
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

                val source = repository.findSourceFacts(planUuid, today = today)

                source shouldBe EvalueringspaaminnelseSourceFacts(
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnummer = organisasjonsnummer,
                    isHidden = false,
                    isRegisteredIncorrectly = false,
                    hasActiveSykmeldingsperiode = true,
                )
                source.toString().contains(sykmeldtFnr) shouldBe false
                source.toString().contains(NARMESTE_LEDER_ID) shouldBe false
                source.toString().contains(organisasjonsnummer) shouldBe false
            }

            it("reports no active period when all matching sykmeldingsperioder are expired") {
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

                repository.findSourceFacts(planUuid, today = today) shouldBe
                    EvalueringspaaminnelseSourceFacts(
                        sykmeldtFnr = sykmeldtFnr,
                        organisasjonsnummer = organisasjonsnummer,
                        isHidden = false,
                        isRegisteredIncorrectly = false,
                        hasActiveSykmeldingsperiode = false,
                    )
            }

            it("reports no active period when matching sykmeldingsperioder start in the future") {
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

                repository.findSourceFacts(planUuid, today = today) shouldBe
                    EvalueringspaaminnelseSourceFacts(
                        sykmeldtFnr = sykmeldtFnr,
                        organisasjonsnummer = organisasjonsnummer,
                        isHidden = false,
                        isRegisteredIncorrectly = false,
                        hasActiveSykmeldingsperiode = false,
                    )
            }

            it("reports no active period when matching sykmeldingsperioder are invalidated") {
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

                repository.findSourceFacts(planUuid, today = today) shouldBe
                    EvalueringspaaminnelseSourceFacts(
                        sykmeldtFnr = sykmeldtFnr,
                        organisasjonsnummer = organisasjonsnummer,
                        isHidden = false,
                        isRegisteredIncorrectly = false,
                        hasActiveSykmeldingsperiode = false,
                    )
            }

            it("returns not found when the source oppfolgingsplan does not exist") {
                repository.findSourceFacts(UUID.randomUUID(), today = today) shouldBe null
            }

            it("reports when the source oppfolgingsplan is hidden") {
                val planUuid = TestDB.database.persistPlanForSourceLookup(
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnummer = organisasjonsnummer,
                    skjultFra = Instant.parse("2026-05-19T10:00:00Z"),
                )
                sykmeldingsperiodeRepository.storePeriod(
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnummer = organisasjonsnummer,
                    sykmeldingId = "active-hidden-plan",
                    fom = today.minusDays(2),
                    tom = today.plusDays(2),
                )

                repository.findSourceFacts(planUuid, today = today) shouldBe
                    EvalueringspaaminnelseSourceFacts(
                        sykmeldtFnr = sykmeldtFnr,
                        organisasjonsnummer = organisasjonsnummer,
                        isHidden = true,
                        isRegisteredIncorrectly = false,
                        hasActiveSykmeldingsperiode = true,
                    )
            }

            it("reports when the source oppfolgingsplan is registered as incorrect") {
                val planUuid = TestDB.database.persistPlanForSourceLookup(
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnummer = organisasjonsnummer,
                    feilregistrert = Instant.parse("2026-05-19T10:00:00Z"),
                )
                sykmeldingsperiodeRepository.storePeriod(
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnummer = organisasjonsnummer,
                    sykmeldingId = "active-incorrect-plan",
                    fom = today.minusDays(2),
                    tom = today.plusDays(2),
                )

                repository.findSourceFacts(planUuid, today = today) shouldBe
                    EvalueringspaaminnelseSourceFacts(
                        sykmeldtFnr = sykmeldtFnr,
                        organisasjonsnummer = organisasjonsnummer,
                        isHidden = false,
                        isRegisteredIncorrectly = true,
                        hasActiveSykmeldingsperiode = true,
                    )
            }
        }
    })

private fun DatabaseInterface.persistPlanForSourceLookup(
    sykmeldtFnr: String,
    organisasjonsnummer: String,
    skjultFra: Instant? = null,
    feilregistrert: Instant? = null,
): UUID = persistOppfolgingsplan(
    defaultPersistedOppfolgingsplan().copy(
        sykmeldtFnr = sykmeldtFnr,
        narmesteLederId = NARMESTE_LEDER_ID,
        organisasjonsnummer = organisasjonsnummer,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        skjultFra = skjultFra,
        feilregistrert = feilregistrert,
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
