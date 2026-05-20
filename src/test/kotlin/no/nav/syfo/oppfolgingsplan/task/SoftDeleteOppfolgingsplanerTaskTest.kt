package no.nav.syfo.oppfolgingsplan.task

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.mockk
import no.nav.syfo.TestDB
import no.nav.syfo.defaultPersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.db.findAllOppfolgingsplanerBy
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanBy
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanerForDokumentportenPublisering
import no.nav.syfo.oppfolgingsplan.db.softDeleteExpiredOppfolgingsplaner
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.persistOppfolgingsplan
import no.nav.syfo.sykmelding.db.SykmeldingsperiodeRepository
import no.nav.syfo.sykmelding.db.domain.SykmeldingsperiodeToStore
import no.nav.syfo.varsel.EsyfovarselProducer
import java.time.Instant
import java.time.LocalDate

class SoftDeleteOppfolgingsplanerTaskTest :
    DescribeSpec({
        val testDb = TestDB.database
        val sykmeldingsperiodeRepository = SykmeldingsperiodeRepository(testDb)

        fun service() = OppfolgingsplanService(
            database = testDb,
            esyfovarselProducer = mockk<EsyfovarselProducer>(relaxed = true),
            pdlService = mockk(relaxed = true),
            aaregService = mockk(relaxed = true),
        )

        fun softDeleteAll(batchSize: Int): Pair<Int, Int> {
            var total = 0
            var iterations = 0
            var count: Int

            do {
                count = testDb.softDeleteExpiredOppfolgingsplaner(batchSize)
                if (count > 0) {
                    iterations++
                }
                total += count
            } while (count > 0)

            return total to iterations
        }

        beforeTest {
            clearAllMocks()
            TestDB.clearAllData()
        }

        describe("softDeleteExpiredOppfolgingsplaner") {
            it("soft-deletes plan with last tom 7 months ago") {
                val plan = defaultPersistedOppfolgingsplan()
                val planUuid = testDb.persistOppfolgingsplan(plan)
                sykmeldingsperiodeRepository.storeSykmeldingsperioder(
                    listOf(
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = plan.sykmeldtFnr,
                            organisasjonsnummer = plan.organisasjonsnummer,
                            sykmeldingId = "sykmelding-1",
                            fom = LocalDate.now().minusMonths(8),
                            tom = LocalDate.now().minusMonths(7),
                        ),
                    ),
                )

                service().softDeleteExpiredOppfolgingsplaner() shouldBe 1

                testDb.findOppfolgingsplanBy(planUuid, inkluderSkjulte = true)?.skjultFra.shouldNotBeNull()
            }

            it("does not soft-delete plan with last tom 5 months ago") {
                val plan = defaultPersistedOppfolgingsplan()
                val planUuid = testDb.persistOppfolgingsplan(plan)
                sykmeldingsperiodeRepository.storeSykmeldingsperioder(
                    listOf(
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = plan.sykmeldtFnr,
                            organisasjonsnummer = plan.organisasjonsnummer,
                            sykmeldingId = "sykmelding-2",
                            fom = LocalDate.now().minusMonths(6),
                            tom = LocalDate.now().minusMonths(5),
                        ),
                    ),
                )

                service().softDeleteExpiredOppfolgingsplaner() shouldBe 0

                testDb.findOppfolgingsplanBy(planUuid, inkluderSkjulte = true)?.skjultFra.shouldBeNull()
            }

            it("does not soft-delete plan with last tom exactly 6 months ago") {
                val plan = defaultPersistedOppfolgingsplan()
                val planUuid = testDb.persistOppfolgingsplan(plan)
                sykmeldingsperiodeRepository.storeSykmeldingsperioder(
                    listOf(
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = plan.sykmeldtFnr,
                            organisasjonsnummer = plan.organisasjonsnummer,
                            sykmeldingId = "sykmelding-boundary-1",
                            fom = LocalDate.now().minusMonths(6).minusDays(14),
                            tom = LocalDate.now().minusMonths(6),
                        ),
                    ),
                )

                service().softDeleteExpiredOppfolgingsplaner() shouldBe 0

                testDb.findOppfolgingsplanBy(planUuid, inkluderSkjulte = true)?.skjultFra.shouldBeNull()
            }

            it("soft-deletes plan with last tom exactly 6 months and 1 day ago") {
                val plan = defaultPersistedOppfolgingsplan()
                val planUuid = testDb.persistOppfolgingsplan(plan)
                sykmeldingsperiodeRepository.storeSykmeldingsperioder(
                    listOf(
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = plan.sykmeldtFnr,
                            organisasjonsnummer = plan.organisasjonsnummer,
                            sykmeldingId = "sykmelding-boundary-2",
                            fom = LocalDate.now().minusMonths(6).minusDays(15),
                            tom = LocalDate.now().minusMonths(6).minusDays(1),
                        ),
                    ),
                )

                service().softDeleteExpiredOppfolgingsplaner() shouldBe 1

                testDb.findOppfolgingsplanBy(planUuid, inkluderSkjulte = true)?.skjultFra.shouldNotBeNull()
            }

            it("does not soft-delete plan when newer sykmeldingsperiode resets cutoff") {
                val plan = defaultPersistedOppfolgingsplan()
                val planUuid = testDb.persistOppfolgingsplan(plan)
                sykmeldingsperiodeRepository.storeSykmeldingsperioder(
                    listOf(
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = plan.sykmeldtFnr,
                            organisasjonsnummer = plan.organisasjonsnummer,
                            sykmeldingId = "sykmelding-3",
                            fom = LocalDate.now().minusMonths(8),
                            tom = LocalDate.now().minusMonths(7),
                        ),
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = plan.sykmeldtFnr,
                            organisasjonsnummer = plan.organisasjonsnummer,
                            sykmeldingId = "sykmelding-3",
                            fom = LocalDate.now().minusMonths(3),
                            tom = LocalDate.now().minusMonths(2),
                        ),
                    ),
                )

                service().softDeleteExpiredOppfolgingsplaner() shouldBe 0

                testDb.findOppfolgingsplanBy(planUuid, inkluderSkjulte = true)?.skjultFra.shouldBeNull()
            }

            it("does not soft-delete plan when there are no sykmeldingsperioder") {
                val plan = defaultPersistedOppfolgingsplan()
                val planUuid = testDb.persistOppfolgingsplan(plan)

                service().softDeleteExpiredOppfolgingsplaner() shouldBe 0

                testDb.findOppfolgingsplanBy(planUuid, inkluderSkjulte = true)?.skjultFra.shouldBeNull()
            }

            it("does not soft-delete plan when only invalidated sykmeldingsperioder exist") {
                val plan = defaultPersistedOppfolgingsplan()
                val planUuid = testDb.persistOppfolgingsplan(plan)
                sykmeldingsperiodeRepository.storeSykmeldingsperioder(
                    listOf(
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = plan.sykmeldtFnr,
                            organisasjonsnummer = plan.organisasjonsnummer,
                            sykmeldingId = "sykmelding-invalidated-only",
                            fom = LocalDate.now().minusMonths(8),
                            tom = LocalDate.now().minusMonths(7),
                        ),
                    ),
                )
                sykmeldingsperiodeRepository.invalidateSykmelding("sykmelding-invalidated-only")

                service().softDeleteExpiredOppfolgingsplaner() shouldBe 0

                testDb.findOppfolgingsplanBy(planUuid, inkluderSkjulte = true)?.skjultFra.shouldBeNull()
            }

            it("ignores invalidated sykmeldingsperioder when a newer valid period exists") {
                val plan = defaultPersistedOppfolgingsplan()
                val planUuid = testDb.persistOppfolgingsplan(plan)
                sykmeldingsperiodeRepository.storeSykmeldingsperioder(
                    listOf(
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = plan.sykmeldtFnr,
                            organisasjonsnummer = plan.organisasjonsnummer,
                            sykmeldingId = "sykmelding-invalidated",
                            fom = LocalDate.now().minusMonths(8),
                            tom = LocalDate.now().minusMonths(7),
                        ),
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = plan.sykmeldtFnr,
                            organisasjonsnummer = plan.organisasjonsnummer,
                            sykmeldingId = "sykmelding-valid",
                            fom = LocalDate.now().minusMonths(3),
                            tom = LocalDate.now().minusMonths(2),
                        ),
                    ),
                )
                sykmeldingsperiodeRepository.invalidateSykmelding("sykmelding-invalidated")

                service().softDeleteExpiredOppfolgingsplaner() shouldBe 0

                testDb.findOppfolgingsplanBy(planUuid, inkluderSkjulte = true)?.skjultFra.shouldBeNull()
            }

            it("soft-deletes plan when only newer sykmeldingsperiode is invalidated") {
                val plan = defaultPersistedOppfolgingsplan()
                val planUuid = testDb.persistOppfolgingsplan(plan)
                sykmeldingsperiodeRepository.storeSykmeldingsperioder(
                    listOf(
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = plan.sykmeldtFnr,
                            organisasjonsnummer = plan.organisasjonsnummer,
                            sykmeldingId = "sykmelding-old-valid",
                            fom = LocalDate.now().minusMonths(8),
                            tom = LocalDate.now().minusMonths(7),
                        ),
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = plan.sykmeldtFnr,
                            organisasjonsnummer = plan.organisasjonsnummer,
                            sykmeldingId = "sykmelding-new-invalidated",
                            fom = LocalDate.now().minusMonths(3),
                            tom = LocalDate.now().minusMonths(2),
                        ),
                    ),
                )
                sykmeldingsperiodeRepository.invalidateSykmelding("sykmelding-new-invalidated")

                service().softDeleteExpiredOppfolgingsplaner() shouldBe 1

                testDb.findOppfolgingsplanBy(planUuid, inkluderSkjulte = true)?.skjultFra.shouldNotBeNull()
            }

            it("soft-deletes all expired plans across multiple batches") {
                val sykmeldtFnr = "12345678901"
                val planUuids = (1..5).map { index ->
                    testDb.persistOppfolgingsplan(
                        defaultPersistedOppfolgingsplan().copy(
                            uuid = java.util.UUID.randomUUID(),
                            sykmeldtFnr = sykmeldtFnr,
                            organisasjonsnummer = "12345678$index",
                        ),
                    )
                }
                sykmeldingsperiodeRepository.storeSykmeldingsperioder(
                    (1..5).map { index ->
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = sykmeldtFnr,
                            organisasjonsnummer = "12345678$index",
                            sykmeldingId = "sykmelding-batch-$index",
                            fom = LocalDate.now().minusMonths(8),
                            tom = LocalDate.now().minusMonths(7),
                        )
                    },
                )

                val (softDeletedCount, iterations) = softDeleteAll(batchSize = 2)

                softDeletedCount shouldBe 5
                iterations shouldBe 3
                planUuids.forEach { planUuid ->
                    testDb.findOppfolgingsplanBy(planUuid, inkluderSkjulte = true)?.skjultFra.shouldNotBeNull()
                }
            }
        }

        describe("filtrering") {
            it("hides soft-deleted plans when inkluderSkjulte is false") {
                val hiddenPlan = defaultPersistedOppfolgingsplan().copy(skjultFra = Instant.now())
                val hiddenUuid = testDb.persistOppfolgingsplan(hiddenPlan)

                testDb.findAllOppfolgingsplanerBy(hiddenPlan.sykmeldtFnr) shouldBe emptyList()
                testDb.findOppfolgingsplanBy(hiddenUuid).shouldBeNull()
            }

            it("returns soft-deleted plans when inkluderSkjulte is true") {
                val hiddenPlan = defaultPersistedOppfolgingsplan().copy(skjultFra = Instant.now())
                val hiddenUuid = testDb.persistOppfolgingsplan(hiddenPlan)

                testDb.findAllOppfolgingsplanerBy(hiddenPlan.sykmeldtFnr, inkluderSkjulte = true).map { it.uuid } shouldBe listOf(hiddenUuid)
                testDb.findOppfolgingsplanBy(hiddenUuid, inkluderSkjulte = true)?.uuid shouldBe hiddenUuid
            }

            it("filters out plans that are both hidden and feilregistrert") {
                val hiddenFeilregistrertPlan = defaultPersistedOppfolgingsplan().copy(
                    skjultFra = Instant.now(),
                    feilregistrertAarsak = "Opprettet på feil person",
                )
                val hiddenFeilregistrertUuid = testDb.persistOppfolgingsplan(hiddenFeilregistrertPlan)

                testDb.findAllOppfolgingsplanerBy(hiddenFeilregistrertPlan.sykmeldtFnr, inkluderSkjulte = true) shouldBe emptyList()
                testDb.findOppfolgingsplanBy(hiddenFeilregistrertUuid, inkluderSkjulte = true).shouldBeNull()
            }
        }

        describe("dokumentporten") {
            it("returns plans regardless of skjult_fra") {
                val visibleUuid = testDb.persistOppfolgingsplan(defaultPersistedOppfolgingsplan())
                val hiddenUuid = testDb.persistOppfolgingsplan(
                    defaultPersistedOppfolgingsplan().copy(
                        skjultFra = Instant.now(),
                    ),
                )

                val planerForPublisering = testDb.findOppfolgingsplanerForDokumentportenPublisering()

                planerForPublisering.map { it.uuid }.sortedBy { it.toString() } shouldBe listOf(visibleUuid, hiddenUuid).sortedBy { it.toString() }
            }
        }
    })
