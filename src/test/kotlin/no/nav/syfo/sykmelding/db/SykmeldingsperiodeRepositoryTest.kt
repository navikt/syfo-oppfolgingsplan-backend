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
        val today = LocalDate.of(2025, 6, 20)
        val sykmeldtFnr = "12345678901"
        val organisasjonsnummer = "987654321"

        beforeEach {
            TestDB.clearAllData()
        }

        fun sykmeldingsperiodeToStore(
            sykmeldingId: String,
            fom: LocalDate,
            tom: LocalDate,
        ) = SykmeldingsperiodeToStore(
            sykmeldtFnr = sykmeldtFnr,
            organisasjonsnummer = organisasjonsnummer,
            sykmeldingId = sykmeldingId,
            fom = fom,
            tom = tom,
        )

        describe("storeSykmeldingsperioder") {
            it("stores sykmeldingsperioder") {
                val insertedRows = repository.storeSykmeldingsperioder(
                    listOf(
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = sykmeldtFnr,
                            organisasjonsnummer = organisasjonsnummer,
                            sykmeldingId = "sykmelding-1",
                            fom = LocalDate.of(2025, 1, 1),
                            tom = LocalDate.of(2025, 1, 31),
                        ),
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = sykmeldtFnr,
                            organisasjonsnummer = organisasjonsnummer,
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
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnummer = organisasjonsnummer,
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
                            sykmeldtFnr = sykmeldtFnr,
                            organisasjonsnummer = organisasjonsnummer,
                            sykmeldingId = "sykmelding-3",
                            fom = LocalDate.of(2025, 4, 1),
                            tom = LocalDate.of(2025, 4, 30),
                        ),
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = sykmeldtFnr,
                            organisasjonsnummer = organisasjonsnummer,
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
            it("returns earliest fom in a continuous chain ending in an active period today") {
                repository.storeSykmeldingsperioder(
                    listOf(
                        sykmeldingsperiodeToStore(
                            sykmeldingId = "sykmelding-old",
                            fom = LocalDate.of(2025, 1, 1),
                            tom = LocalDate.of(2025, 1, 31),
                        ),
                        sykmeldingsperiodeToStore(
                            sykmeldingId = "sykmelding-current",
                            fom = LocalDate.of(2025, 6, 1),
                            tom = LocalDate.of(2025, 6, 30),
                        ),
                        sykmeldingsperiodeToStore(
                            sykmeldingId = "sykmelding-connected",
                            fom = LocalDate.of(2025, 5, 15),
                            tom = LocalDate.of(2025, 5, 31),
                        ),
                    ),
                )

                val earliestFom = repository.findEarliestFom(
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnummer = organisasjonsnummer,
                    today = today,
                )

                earliestFom shouldBe LocalDate.of(2025, 5, 15)
            }

            it("stops at the first gap when searching backwards") {
                repository.storeSykmeldingsperioder(
                    listOf(
                        sykmeldingsperiodeToStore(
                            sykmeldingId = "sykmelding-current",
                            fom = LocalDate.of(2025, 6, 1),
                            tom = LocalDate.of(2025, 6, 30),
                        ),
                        sykmeldingsperiodeToStore(
                            sykmeldingId = "sykmelding-gap",
                            fom = LocalDate.of(2025, 5, 10),
                            tom = LocalDate.of(2025, 5, 30),
                        ),
                    ),
                )

                val earliestFom = repository.findEarliestFom(
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnummer = organisasjonsnummer,
                    today = today,
                )

                earliestFom shouldBe LocalDate.of(2025, 6, 1)
            }

            it("treats overlapping and adjacent periods as continuous") {
                repository.storeSykmeldingsperioder(
                    listOf(
                        sykmeldingsperiodeToStore(
                            sykmeldingId = "sykmelding-active",
                            fom = LocalDate.of(2025, 6, 15),
                            tom = LocalDate.of(2025, 6, 30),
                        ),
                        sykmeldingsperiodeToStore(
                            sykmeldingId = "sykmelding-overlap",
                            fom = LocalDate.of(2025, 6, 1),
                            tom = LocalDate.of(2025, 6, 20),
                        ),
                        sykmeldingsperiodeToStore(
                            sykmeldingId = "sykmelding-adjacent",
                            fom = LocalDate.of(2025, 5, 20),
                            tom = LocalDate.of(2025, 5, 31),
                        ),
                    ),
                )

                val earliestFom = repository.findEarliestFom(
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnummer = organisasjonsnummer,
                    today = today,
                )

                earliestFom shouldBe LocalDate.of(2025, 5, 20)
            }

            it("returns null when no non-invalidated period is active today") {
                repository.storeSykmeldingsperioder(
                    listOf(
                        sykmeldingsperiodeToStore(
                            sykmeldingId = "sykmelding-ended",
                            fom = LocalDate.of(2025, 5, 1),
                            tom = LocalDate.of(2025, 5, 31),
                        ),
                    ),
                )

                val earliestFom = repository.findEarliestFom(
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnummer = organisasjonsnummer,
                    today = today,
                )

                earliestFom shouldBe null
            }

            it("ignores invalidated periods when finding the continuous chain") {
                repository.storeSykmeldingsperioder(
                    listOf(
                        sykmeldingsperiodeToStore(
                            sykmeldingId = "sykmelding-active",
                            fom = LocalDate.of(2025, 6, 1),
                            tom = LocalDate.of(2025, 6, 30),
                        ),
                        sykmeldingsperiodeToStore(
                            sykmeldingId = "sykmelding-invalidated",
                            fom = LocalDate.of(2025, 5, 1),
                            tom = LocalDate.of(2025, 5, 31),
                        ),
                    ),
                )
                repository.invalidateSykmelding("sykmelding-invalidated")

                val earliestFom = repository.findEarliestFom(
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnummer = organisasjonsnummer,
                    today = today,
                )

                earliestFom shouldBe LocalDate.of(2025, 6, 1)
            }

            it("does not include periods that ended more than 50 days before today") {
                repository.storeSykmeldingsperioder(
                    listOf(
                        sykmeldingsperiodeToStore(
                            sykmeldingId = "sykmelding-outside-lookback",
                            fom = LocalDate.of(2025, 4, 20),
                            tom = LocalDate.of(2025, 4, 30),
                        ),
                        sykmeldingsperiodeToStore(
                            sykmeldingId = "sykmelding-active",
                            fom = LocalDate.of(2025, 5, 1),
                            tom = LocalDate.of(2025, 6, 30),
                        ),
                    ),
                )

                val earliestFom = repository.findEarliestFom(
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnummer = organisasjonsnummer,
                    today = today,
                )

                earliestFom shouldBe LocalDate.of(2025, 5, 1)
            }
        }
    })
