package no.nav.syfo.sykmelding.db

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.syfo.TestDB
import no.nav.syfo.sykmelding.db.domain.SykmeldingsperiodeToStore
import java.time.LocalDate

class SykmeldingsperiodeRepositoryTest :
    DescribeSpec({
        val repository = SykmeldingsperiodeRepository(TestDB.database)

        beforeEach {
            TestDB.clearAllData()
        }

        describe("storeSykmeldingsperioder") {
            it("stores sykmeldingsperioder") {
                val insertedRows = repository.storeSykmeldingsperioder(
                    listOf(
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = "12345678901",
                            organisasjonsnummer = "987654321",
                            sykmeldingId = "sykmelding-1",
                            fom = LocalDate.of(2025, 1, 1),
                            tom = LocalDate.of(2025, 1, 31),
                        ),
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = "12345678901",
                            organisasjonsnummer = "987654321",
                            sykmeldingId = "sykmelding-1",
                            fom = LocalDate.of(2025, 2, 1),
                            tom = LocalDate.of(2025, 2, 28),
                        ),
                    ),
                )

                val persisted = repository.findBySykmeldingId("sykmelding-1")

                insertedRows shouldBe 2
                persisted.shouldHaveSize(2)
                persisted.map { it.fom } shouldBe listOf(
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 2, 1),
                )
                persisted.all { it.invalidatedAt == null } shouldBe true
            }

            it("is idempotent for duplicate periods") {
                val sykmeldingsperiode = SykmeldingsperiodeToStore(
                    sykmeldtFnr = "12345678901",
                    organisasjonsnummer = "987654321",
                    sykmeldingId = "sykmelding-2",
                    fom = LocalDate.of(2025, 3, 1),
                    tom = LocalDate.of(2025, 3, 31),
                )

                repository.storeSykmeldingsperioder(listOf(sykmeldingsperiode))
                val duplicateInsertCount = repository.storeSykmeldingsperioder(listOf(sykmeldingsperiode))

                val persisted = repository.findBySykmeldingId("sykmelding-2")

                duplicateInsertCount shouldBe 0
                persisted.shouldHaveSize(1)
            }
        }

        describe("invalidateSykmelding") {
            it("sets invalidatedAt for all matching rows") {
                repository.storeSykmeldingsperioder(
                    listOf(
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = "12345678901",
                            organisasjonsnummer = "987654321",
                            sykmeldingId = "sykmelding-3",
                            fom = LocalDate.of(2025, 4, 1),
                            tom = LocalDate.of(2025, 4, 30),
                        ),
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = "12345678901",
                            organisasjonsnummer = "987654321",
                            sykmeldingId = "sykmelding-3",
                            fom = LocalDate.of(2025, 5, 1),
                            tom = LocalDate.of(2025, 5, 31),
                        ),
                    ),
                )

                val invalidatedRows = repository.invalidateSykmelding("sykmelding-3")
                val persisted = repository.findBySykmeldingId("sykmelding-3")

                invalidatedRows shouldBe 2
                persisted.shouldHaveSize(2)
                persisted.all { it.invalidatedAt != null } shouldBe true
            }
        }

        describe("findOrganisasjonerMedAktivSykmeldingsperiode") {
            it("returns distinct active organisasjonsnumre within grace period") {
                val today = LocalDate.now()

                repository.storeSykmeldingsperioder(
                    listOf(
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = "12345678901",
                            organisasjonsnummer = "111111111",
                            sykmeldingId = "sykmelding-4",
                            fom = today.minusDays(10),
                            tom = today.plusDays(5),
                        ),
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = "12345678901",
                            organisasjonsnummer = "222222222",
                            sykmeldingId = "sykmelding-5",
                            fom = today.minusDays(30),
                            tom = today.minusDays(FORESPORSEL_GRACE_PERIOD_DAYS),
                        ),
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = "12345678901",
                            organisasjonsnummer = "111111111",
                            sykmeldingId = "sykmelding-6",
                            fom = today.minusDays(3),
                            tom = today.plusDays(10),
                        ),
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = "12345678901",
                            organisasjonsnummer = "333333333",
                            sykmeldingId = "sykmelding-7",
                            fom = today.plusDays(1),
                            tom = today.plusDays(20),
                        ),
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = "12345678901",
                            organisasjonsnummer = "444444444",
                            sykmeldingId = "sykmelding-8",
                            fom = today.minusDays(40),
                            tom = today.minusDays(FORESPORSEL_GRACE_PERIOD_DAYS + 1),
                        ),
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = "12345678902",
                            organisasjonsnummer = "555555555",
                            sykmeldingId = "sykmelding-9",
                            fom = today.minusDays(10),
                            tom = today.plusDays(10),
                        ),
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = "12345678901",
                            organisasjonsnummer = "666666666",
                            sykmeldingId = "sykmelding-10",
                            fom = today.minusDays(10),
                            tom = today.plusDays(10),
                        ),
                    ),
                )
                repository.invalidateSykmelding("sykmelding-10")

                val aktiveOrganisasjoner = repository.findOrganisasjonerMedAktivSykmeldingsperiode("12345678901")

                aktiveOrganisasjoner shouldContainExactlyInAnyOrder listOf(
                    "111111111",
                    "222222222",
                )
            }

            it("returns empty list when no active periods exist") {
                val aktiveOrganisasjoner = repository.findOrganisasjonerMedAktivSykmeldingsperiode("12345678901")

                aktiveOrganisasjoner shouldBe emptyList()
            }
        }
    })
