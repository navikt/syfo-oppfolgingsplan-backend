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

class OppfolgingsplanEvalueringPaaminnelseSourceRepositoryTest :
    DescribeSpec({
        val sykmeldingsperiodeRepository = SykmeldingsperiodeRepository(TestDB.database)
        val sourceRepository = OppfolgingsplanEvalueringPaaminnelseSourceRepository(TestDB.database)
        val oslo = ZoneId.of("Europe/Oslo")
        val today = LocalDate.of(2026, 5, 20)
        val todayClock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        val sykmeldtFnr = "12345678901"
        val sykmeldtFullName = "Kari Normann"
        val organisasjonsnummer = "999999999"
        val organisasjonsnavn = "ARNESEN, HOLM OG BAKKEN"

        beforeTest {
            TestDB.clearAllData()
        }

        describe("find") {
            it("returns eligible source data when a matching active sykmeldingsperiode exists") {
                val planUuid = TestDB.database.persistPlanForSourceLookup(
                    sykmeldtFnr = sykmeldtFnr,
                    sykmeldtFullName = sykmeldtFullName,
                    organisasjonsnummer = organisasjonsnummer,
                    organisasjonsnavn = organisasjonsnavn,
                    evalueringsdato = LocalDate.of(2026, 6, 20),
                )
                sykmeldingsperiodeRepository.storeSykmeldingsperioder(
                    listOf(
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = sykmeldtFnr,
                            organisasjonsnummer = organisasjonsnummer,
                            sykmeldingId = "active-sykmelding",
                            fom = today.minusDays(2),
                            tom = today.plusDays(2),
                        ),
                    ),
                )

                val source = sourceRepository.find(
                    planUuid,
                    clock = todayClock,
                )

                source shouldBe OppfolgingsplanEvalueringPaaminnelseSource.Eligible(
                    OppfolgingsplanEvalueringPaaminnelseSourceData(
                        sykmeldtFnr = sykmeldtFnr,
                        sykmeldtFullName = sykmeldtFullName,
                        organisasjonsnummer = organisasjonsnummer,
                        organisasjonsnavn = organisasjonsnavn,
                        evalueringsdato = LocalDate.of(2026, 6, 20),
                    ),
                )
                source.toString().contains(sykmeldtFnr) shouldBe false
                source.toString().contains(sykmeldtFullName) shouldBe false
                source.toString().contains(organisasjonsnummer) shouldBe false
            }

            it("returns no longer eligible when all matching sykmeldingsperioder are expired") {
                val planUuid = TestDB.database.persistPlanForSourceLookup(
                    sykmeldtFnr = sykmeldtFnr,
                    sykmeldtFullName = sykmeldtFullName,
                    organisasjonsnummer = organisasjonsnummer,
                    organisasjonsnavn = organisasjonsnavn,
                    evalueringsdato = LocalDate.of(2026, 6, 20),
                )
                sykmeldingsperiodeRepository.storeSykmeldingsperioder(
                    listOf(
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = sykmeldtFnr,
                            organisasjonsnummer = organisasjonsnummer,
                            sykmeldingId = "expired-sykmelding",
                            fom = today.minusDays(10),
                            tom = today.minusDays(1),
                        ),
                    ),
                )

                sourceRepository.find(
                    planUuid,
                    clock = todayClock,
                ) shouldBe
                    OppfolgingsplanEvalueringPaaminnelseSource.NoLongerEligible
            }

            it("returns no longer eligible when matching sykmeldingsperioder start in the future") {
                val planUuid = TestDB.database.persistPlanForSourceLookup(
                    sykmeldtFnr = sykmeldtFnr,
                    sykmeldtFullName = sykmeldtFullName,
                    organisasjonsnummer = organisasjonsnummer,
                    organisasjonsnavn = organisasjonsnavn,
                    evalueringsdato = LocalDate.of(2026, 6, 20),
                )
                sykmeldingsperiodeRepository.storeSykmeldingsperioder(
                    listOf(
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = sykmeldtFnr,
                            organisasjonsnummer = organisasjonsnummer,
                            sykmeldingId = "future-sykmelding",
                            fom = today.plusDays(1),
                            tom = today.plusDays(10),
                        ),
                    ),
                )

                sourceRepository.find(
                    planUuid,
                    clock = todayClock,
                ) shouldBe
                    OppfolgingsplanEvalueringPaaminnelseSource.NoLongerEligible
            }

            it("returns no longer eligible when matching sykmeldingsperioder are invalidated") {
                val planUuid = TestDB.database.persistPlanForSourceLookup(
                    sykmeldtFnr = sykmeldtFnr,
                    sykmeldtFullName = sykmeldtFullName,
                    organisasjonsnummer = organisasjonsnummer,
                    organisasjonsnavn = organisasjonsnavn,
                    evalueringsdato = LocalDate.of(2026, 6, 20),
                )
                sykmeldingsperiodeRepository.storeSykmeldingsperioder(
                    listOf(
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = sykmeldtFnr,
                            organisasjonsnummer = organisasjonsnummer,
                            sykmeldingId = "invalidated-sykmelding",
                            fom = today.minusDays(2),
                            tom = today.plusDays(2),
                        ),
                    ),
                )
                sykmeldingsperiodeRepository.invalidateSykmelding("invalidated-sykmelding")

                sourceRepository.find(
                    planUuid,
                    clock = todayClock,
                ) shouldBe
                    OppfolgingsplanEvalueringPaaminnelseSource.NoLongerEligible
            }

            it("returns not found when the source oppfolgingsplan does not exist") {
                sourceRepository.find(
                    UUID.randomUUID(),
                    clock = todayClock,
                ) shouldBe
                    OppfolgingsplanEvalueringPaaminnelseSource.NotFound
            }

            it("uses Europe/Oslo date when UTC and Oslo are on different calendar dates") {
                val boundaryInstant = Instant.parse("2026-05-20T22:30:00Z")
                val boundaryClock = Clock.fixed(boundaryInstant, ZoneOffset.UTC)
                val osloToday = LocalDate.ofInstant(boundaryInstant, oslo)
                val planUuid = TestDB.database.persistPlanForSourceLookup(
                    sykmeldtFnr = sykmeldtFnr,
                    sykmeldtFullName = sykmeldtFullName,
                    organisasjonsnummer = organisasjonsnummer,
                    organisasjonsnavn = organisasjonsnavn,
                    evalueringsdato = LocalDate.of(2026, 6, 20),
                )
                sykmeldingsperiodeRepository.storeSykmeldingsperioder(
                    listOf(
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = sykmeldtFnr,
                            organisasjonsnummer = organisasjonsnummer,
                            sykmeldingId = "oslo-boundary-sykmelding",
                            fom = osloToday,
                            tom = osloToday,
                        ),
                    ),
                )

                sourceRepository.find(
                    planUuid,
                    clock = boundaryClock,
                ) shouldBe OppfolgingsplanEvalueringPaaminnelseSource.Eligible(
                    OppfolgingsplanEvalueringPaaminnelseSourceData(
                        sykmeldtFnr = sykmeldtFnr,
                        sykmeldtFullName = sykmeldtFullName,
                        organisasjonsnummer = organisasjonsnummer,
                        organisasjonsnavn = organisasjonsnavn,
                        evalueringsdato = LocalDate.of(2026, 6, 20),
                    ),
                )
            }
        }
    })

private fun DatabaseInterface.persistPlanForSourceLookup(
    sykmeldtFnr: String,
    sykmeldtFullName: String,
    organisasjonsnummer: String,
    organisasjonsnavn: String,
    evalueringsdato: LocalDate,
): UUID = persistOppfolgingsplan(
    defaultPersistedOppfolgingsplan().copy(
        sykmeldtFnr = sykmeldtFnr,
        sykmeldtFullName = sykmeldtFullName,
        organisasjonsnummer = organisasjonsnummer,
        organisasjonsnavn = organisasjonsnavn,
        evalueringsdato = evalueringsdato,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        narmesteLederId = UUID.randomUUID().toString(),
    ),
)
