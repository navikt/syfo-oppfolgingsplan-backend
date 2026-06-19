package no.nav.syfo.sykmelding.db

import io.kotest.core.spec.style.DescribeSpec
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

        describe("findEarliestFom") {
            it("returns earliest active fom for sykmeldt and organization") {
                repository.storeSykmeldingsperioder(
                    listOf(
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = "12345678901",
                            organisasjonsnummer = "987654321",
                            sykmeldingId = "sykmelding-old",
                            fom = LocalDate.of(2025, 1, 1),
                            tom = LocalDate.of(2025, 1, 31),
                        ),
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = "12345678901",
                            organisasjonsnummer = "987654321",
                            sykmeldingId = "sykmelding-new",
                            fom = LocalDate.of(2025, 6, 1),
                            tom = LocalDate.of(2025, 6, 30),
                        ),
                    ),
                )

                val earliestFom = repository.findEarliestFom(
                    sykmeldtFnr = "12345678901",
                    organisasjonsnummer = "987654321",
                )

                earliestFom shouldBe LocalDate.of(2025, 1, 1)
            }
        }
    })
