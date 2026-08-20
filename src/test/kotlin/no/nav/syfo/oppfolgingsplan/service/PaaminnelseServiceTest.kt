package no.nav.syfo.oppfolgingsplan.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.plugins.BadRequestException
import no.nav.syfo.TestDB
import no.nav.syfo.defaultPersistedOppfolgingsplan
import no.nav.syfo.defaultSykmeldt
import no.nav.syfo.oppfolgingsplan.db.findPaaminnelseBy
import no.nav.syfo.oppfolgingsplan.db.upsertPaaminnelse
import no.nav.syfo.oppfolgingsplan.dto.PaaminnelseStatus
import no.nav.syfo.persistOppfolgingsplan
import no.nav.syfo.sykmelding.db.SykmeldingsperiodeRepository
import no.nav.syfo.sykmelding.db.domain.SykmeldingsperiodeToStore
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PaaminnelseServiceTest :
    DescribeSpec({
        val fixedClock = Clock.fixed(Instant.parse("2025-06-19T10:00:00Z"), ZoneId.of("Europe/Oslo"))
        val repository = SykmeldingsperiodeRepository(TestDB.database)
        val service = PaaminnelseService(
            database = TestDB.database,
            sykmeldingsperiodeRepository = repository,
            clock = fixedClock,
        )

        beforeTest {
            TestDB.clearAllData()
        }

        fun seedSyketilfelle(
            startDato: LocalDate,
            tom: LocalDate = startDato.plusDays(14),
        ) {
            repository.storeSykmeldingsperioder(
                listOf(
                    SykmeldingsperiodeToStore(
                        sykmeldtFnr = "12345678901",
                        organisasjonsnummer = "orgnummer",
                        sykmeldingId = "sykmelding-service",
                        fom = startDato,
                        tom = tom,
                    ),
                ),
            )
        }

        describe("getPaaminnelseStatus") {
            it("returns SKJULT when there are no active sykmeldingsperioder") {
                val status = service.getPaaminnelseStatus(defaultSykmeldt())

                status.status shouldBe PaaminnelseStatus.SKJULT
            }

            it("returns SKJULT when bestillingsvinduet has passed") {
                seedSyketilfelle(
                    startDato = LocalDate.of(2025, 5, 1),
                    tom = LocalDate.of(2025, 6, 30),
                )

                val status = service.getPaaminnelseStatus(defaultSykmeldt())

                status.status shouldBe PaaminnelseStatus.SKJULT
            }

            it("returns TILGJENGELIG inside the window when no paaminnelse is ordered") {
                seedSyketilfelle(
                    startDato = LocalDate.of(2025, 6, 1),
                    tom = LocalDate.of(2025, 6, 30),
                )

                val status = service.getPaaminnelseStatus(defaultSykmeldt())

                status.status shouldBe PaaminnelseStatus.TILGJENGELIG
            }

            it("returns TILGJENGELIG when an oppfolgingsplan exists from after synligFra") {
                val synligFra = LocalDate.of(2025, 6, 1)
                seedSyketilfelle(
                    startDato = synligFra,
                    tom = LocalDate.of(2025, 6, 30),
                )
                TestDB.database.persistOppfolgingsplan(
                    defaultPersistedOppfolgingsplan().copy(
                        createdAt = synligFra.atStartOfDay(fixedClock.zone).toInstant().minusSeconds(1),
                    ),
                )

                val status = service.getPaaminnelseStatus(defaultSykmeldt())

                status.status shouldBe PaaminnelseStatus.TILGJENGELIG
            }

            it("returns SKJULT when an oppfolgingsplan already exists but from prior to current syketilfelle") {
                seedSyketilfelle(
                    startDato = LocalDate.of(2025, 6, 1),
                    tom = LocalDate.of(2025, 6, 30),
                )
                TestDB.database.persistOppfolgingsplan(defaultPersistedOppfolgingsplan())

                val status = service.getPaaminnelseStatus(defaultSykmeldt())

                status.status shouldBe PaaminnelseStatus.SKJULT
            }

            it("returns TILGJENGELIG on day 23 after synligFra") {
                val synligFra = LocalDate.of(2025, 5, 27)
                seedSyketilfelle(
                    startDato = synligFra,
                    tom = LocalDate.of(2025, 6, 30),
                )

                val status = service.getPaaminnelseStatus(defaultSykmeldt())

                status.status shouldBe PaaminnelseStatus.TILGJENGELIG
            }

            it("returns TILGJENGELIG on day 24 after synligFra") {
                val synligFra = LocalDate.of(2025, 5, 26)
                seedSyketilfelle(
                    startDato = synligFra,
                    tom = LocalDate.of(2025, 6, 30),
                )

                val status = service.getPaaminnelseStatus(defaultSykmeldt())

                status.status shouldBe PaaminnelseStatus.TILGJENGELIG
            }

            it("returns SKJULT on day 25 after synligFra") {
                val synligFra = LocalDate.of(2025, 5, 25)
                seedSyketilfelle(
                    startDato = synligFra,
                    tom = LocalDate.of(2025, 6, 30),
                )

                val status = service.getPaaminnelseStatus(defaultSykmeldt())

                status.status shouldBe PaaminnelseStatus.SKJULT
            }

            it("returns BESTILT inside the window when paaminnelse is ordered") {
                seedSyketilfelle(
                    startDato = LocalDate.of(2025, 6, 1),
                    tom = LocalDate.of(2025, 6, 30),
                )
                service.activatePaaminnelse(defaultSykmeldt())

                val status = service.getPaaminnelseStatus(defaultSykmeldt())

                status.status shouldBe PaaminnelseStatus.BESTILT
            }

            it("returns TILGJENGELIG when paaminnelse belongs to a previous syketilfelle") {
                val previousSyketilfelleStart = LocalDate.of(2025, 5, 1)
                seedSyketilfelle(previousSyketilfelleStart, LocalDate.of(2025, 5, 31))
                val previousSykmeldingsperiodeId = repository.findBySykmeldingId("sykmelding-service").single().id
                TestDB.database.upsertPaaminnelse(
                    sykmeldt = defaultSykmeldt(),
                    bestilt = true,
                    sykmeldingsperiodeId = previousSykmeldingsperiodeId,
                )
                seedSyketilfelle(LocalDate.of(2025, 6, 10), LocalDate.of(2025, 6, 30))

                val status = service.getPaaminnelseStatus(defaultSykmeldt())

                status.status shouldBe PaaminnelseStatus.TILGJENGELIG
            }
        }

        describe("bestillPaaminnelse and avbestillPaaminnelse") {
            it("returns explicit BESTILT and TILGJENGELIG contract values") {
                seedSyketilfelle(
                    startDato = LocalDate.of(2025, 6, 1),
                    tom = LocalDate.of(2025, 6, 30),
                )

                val bestilt = service.activatePaaminnelse(defaultSykmeldt())
                val avbestilt = service.deactivatePaaminnelse(defaultSykmeldt())

                bestilt.status shouldBe PaaminnelseStatus.BESTILT
                avbestilt.status shouldBe PaaminnelseStatus.TILGJENGELIG
                TestDB.database.findPaaminnelseBy("12345678901", "orgnummer")?.bestilt shouldBe false
            }

            it("rejects activatePaaminnelse when status is SKJULT because an oppfolgingsplan already exists in the current syketilfelle") {
                val synligFra = LocalDate.of(2025, 6, 1)
                seedSyketilfelle(
                    startDato = synligFra,
                    tom = LocalDate.of(2025, 6, 30),
                )
                TestDB.database.persistOppfolgingsplan(
                    defaultPersistedOppfolgingsplan().copy(
                        createdAt = synligFra.atStartOfDay(fixedClock.zone).toInstant(),
                    ),
                )

                shouldThrow<BadRequestException> {
                    service.activatePaaminnelse(defaultSykmeldt())
                }

                TestDB.database.findPaaminnelseBy("12345678901", "orgnummer") shouldBe null
            }

            it("rejects deactivatePaaminnelse when status is SKJULT because the ordering window has passed") {
                seedSyketilfelle(
                    startDato = LocalDate.of(2025, 5, 1),
                    tom = LocalDate.of(2025, 6, 30),
                )
                TestDB.database.upsertPaaminnelse(
                    sykmeldt = defaultSykmeldt(),
                    bestilt = true,
                    sykmeldingsperiodeId = repository.findBySykmeldingId("sykmelding-service").single().id,
                )

                shouldThrow<BadRequestException> {
                    service.deactivatePaaminnelse(defaultSykmeldt())
                }

                TestDB.database.findPaaminnelseBy("12345678901", "orgnummer")?.bestilt shouldBe true
            }
        }
    })
