package no.nav.syfo.oppfolgingsplan.service
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import no.nav.syfo.TestDB
import no.nav.syfo.aareg.AaregService
import no.nav.syfo.aareg.Stillingsinformasjon
import no.nav.syfo.defaultOppfolgingsplan
import no.nav.syfo.defaultPersistedOppfolgingsplan
import no.nav.syfo.defaultPersistedOppfolgingsplanUtkast
import no.nav.syfo.defaultSykmeldt
import no.nav.syfo.findOppfolgingsplanUtkastByNarmesteLederId
import no.nav.syfo.findVarselPublishedAtByOppfolgingsplanId
import no.nav.syfo.oppfolgingsplan.db.deleteExpiredOppfolgingsplanUtkast
import no.nav.syfo.oppfolgingsplan.db.findEventId
import no.nav.syfo.pdl.PdlService
import no.nav.syfo.persistOppfolgingsplan
import no.nav.syfo.persistOppfolgingsplanUtkast
import no.nav.syfo.setOppfolgingsplanUtkastUpdatedAt
import no.nav.syfo.varsel.EsyfovarselProducer
import no.nav.syfo.varsel.budstikka.infrastructure.BudstikkaPublisher
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

class OppfolgingsplanServiceTest :
    DescribeSpec({
        describe("Extension function tests") {
            it("toListOppfolgingsplanVeileder should filter out items not delt with veileder") {
                // Arrange
                val deltMedVeileder = defaultPersistedOppfolgingsplan().copy(
                    deltMedVeilederTidspunkt = Instant.now().plus(10, ChronoUnit.MINUTES),
                    skalDelesMedVeileder = true,
                )
                val ikkeDeltMedVeileder = defaultPersistedOppfolgingsplan().copy(
                    deltMedVeilederTidspunkt = null,
                    skalDelesMedVeileder = false,
                )
                val oppfolgingsplaner = listOf(ikkeDeltMedVeileder, deltMedVeileder)

                // Act
                val filtered = oppfolgingsplaner.toListOppfolgingsplanVeileder()
                // Assert

                filtered.size shouldBe 1
                filtered.first().uuid shouldBe deltMedVeileder.uuid
            }

            it("toListOppfolgingsplanVeileder should only filter on deling med veileder") {
                val hiddenFeilregistrert = defaultPersistedOppfolgingsplan().copy(
                    deltMedVeilederTidspunkt = Instant.now().plus(10, ChronoUnit.MINUTES),
                    skalDelesMedVeileder = true,
                    skjultFra = Instant.now(),
                    feilregistrert = Instant.now(),
                    feilregistrertAarsak = "Opprettet på feil person",
                )
                val hiddenButValid = defaultPersistedOppfolgingsplan().copy(
                    deltMedVeilederTidspunkt = Instant.now().plus(20, ChronoUnit.MINUTES),
                    skalDelesMedVeileder = true,
                    skjultFra = Instant.now(),
                    feilregistrertAarsak = null,
                )

                val filtered = listOf(hiddenFeilregistrert, hiddenButValid).toListOppfolgingsplanVeileder()

                filtered.map { it.uuid }.toSet() shouldBe setOf(hiddenFeilregistrert.uuid, hiddenButValid.uuid)
            }
        }
        describe("public function tests") {
            describe("getAndSetNarmestelederFullname") {
                afterTest {
                    TestDB.clearAllData()
                    clearAllMocks(currentThreadOnly = true)
                }
                it("should set narmesteleder fullname when it is missing") {
                    // Arrange
                    val expectedFullname = "Ola Nordmann"
                    val pdlServive = mockk<PdlService>()
                    val service = OppfolgingsplanService(
                        database = TestDB.database,
                        pdlService = pdlServive,
                        esyfovarselProducer = mockk<EsyfovarselProducer>(relaxed = true),
                        budstikkaPublisher = mockk(relaxed = true),
                        aaregService = mockk(relaxed = true),
                    )
                    coEvery { pdlServive.getNameFor(any()) } returns expectedFullname
                    val plan = defaultPersistedOppfolgingsplan().copy(
                        narmesteLederFullName = null,
                    )
                    val persistedPlan = TestDB.database.persistOppfolgingsplan(plan).let {
                        plan.copy(uuid = it)
                    }

                    // Act
                    val result = service.getAndSetNarmestelederFullname(persistedPlan)

                    // Assert
                    result.narmesteLederFullName shouldBe "Ola Nordmann"
                    val fromDb = service.getPersistedOppfolgingsplanByUuid(persistedPlan.uuid)
                    fromDb.narmesteLederFullName shouldBe "Ola Nordmann"
                    coVerify(exactly = 1) { pdlServive.getNameFor(any()) }
                }

                it("should not fetch narmesteleder fullname when it already exists") {
                    // Arrange
                    val expectedFullname = "Ola Nordmann"
                    val pdlServive = mockk<PdlService>()
                    val service = OppfolgingsplanService(
                        database = TestDB.database,
                        pdlService = pdlServive,
                        esyfovarselProducer = mockk<EsyfovarselProducer>(relaxed = true),
                        budstikkaPublisher = mockk(relaxed = true),
                        aaregService = mockk(relaxed = true),
                    )
                    coEvery { pdlServive.getNameFor(any()) } returns expectedFullname
                    val plan = defaultPersistedOppfolgingsplan()
                    val persistedPlan = TestDB.database.persistOppfolgingsplan(plan).let {
                        plan.copy(uuid = it)
                    }

                    // Act
                    val result = service.getAndSetNarmestelederFullname(persistedPlan)

                    // Assert
                    result.narmesteLederFullName shouldBe plan.narmesteLederFullName
                    coVerify(exactly = 0) { pdlServive.getNameFor(any()) }
                }
            }
            describe("createOppfolgingsplan") {
                afterTest {
                    TestDB.clearAllData()
                    clearAllMocks(currentThreadOnly = true)
                }

                it("should persist stillingssnapshot from aareg") {
                    val aaregService = mockk<AaregService>()
                    val budstikkaPublisher = mockk<BudstikkaPublisher>()
                    val service = OppfolgingsplanService(
                        database = TestDB.database,
                        pdlService = mockk(relaxed = true),
                        esyfovarselProducer = mockk(relaxed = true),
                        budstikkaPublisher = budstikkaPublisher,
                        aaregService = aaregService,
                    )
                    coEvery {
                        aaregService.getStillingsinformasjon("12345678901", "orgnummer")
                    } returns Stillingsinformasjon(
                        stillingstittel = "Systemutvikler",
                        stillingsprosent = BigDecimal("80.50"),
                    )
                    coEvery {
                        budstikkaPublisher.publishOppfolgingsplanCreated(any(), any(), any())
                    } just Runs

                    val uuid = service.createOppfolgingsplan(
                        narmesteLederFnr = "10987654321",
                        sykmeldt = defaultSykmeldt(),
                        createOppfolgingsplanRequest = defaultOppfolgingsplan(),
                    )

                    val persisted = service.getPersistedOppfolgingsplanByUuid(uuid)
                    persisted.stillingstittel shouldBe "Systemutvikler"
                    persisted.stillingsprosent shouldBe BigDecimal("80.50")
                    val eventId = TestDB.database.findEventId(uuid)
                    eventId.shouldNotBeNull()
                    coVerify(exactly = 1) {
                        budstikkaPublisher.publishOppfolgingsplanCreated(
                            oppfolgingsplanUuid = uuid,
                            sykmeldtFnr = "12345678901",
                            eventId = eventId,
                        )
                    }
                }

                it("should persist null stillingssnapshot when aareg fails") {
                    val aaregService = mockk<AaregService>()
                    val budstikkaPublisher = mockk<BudstikkaPublisher>()
                    val service = OppfolgingsplanService(
                        database = TestDB.database,
                        pdlService = mockk(relaxed = true),
                        esyfovarselProducer = mockk(relaxed = true),
                        budstikkaPublisher = budstikkaPublisher,
                        aaregService = aaregService,
                    )
                    coEvery {
                        aaregService.getStillingsinformasjon("12345678901", "orgnummer")
                    } throws RuntimeException("boom")
                    coEvery {
                        budstikkaPublisher.publishOppfolgingsplanCreated(any(), any(), any())
                    } answers { thirdArg<UUID>() }

                    val uuid = service.createOppfolgingsplan(
                        narmesteLederFnr = "10987654321",
                        sykmeldt = defaultSykmeldt(),
                        createOppfolgingsplanRequest = defaultOppfolgingsplan(),
                    )

                    val persisted = service.getPersistedOppfolgingsplanByUuid(uuid)
                    persisted.stillingstittel.shouldBeNull()
                    persisted.stillingsprosent.shouldBeNull()
                }

                it("should still create oppfolgingsplan when Budstikka publisher throws") {
                    val aaregService = mockk<AaregService>()
                    val esyfovarselProducer = mockk<EsyfovarselProducer>(relaxed = true)
                    val budstikkaPublisher = mockk<BudstikkaPublisher>()
                    val service = OppfolgingsplanService(
                        database = TestDB.database,
                        pdlService = mockk(relaxed = true),
                        esyfovarselProducer = esyfovarselProducer,
                        budstikkaPublisher = budstikkaPublisher,
                        aaregService = aaregService,
                    )
                    coEvery {
                        aaregService.getStillingsinformasjon("12345678901", "orgnummer")
                    } returns Stillingsinformasjon(
                        stillingstittel = "Systemutvikler",
                        stillingsprosent = BigDecimal("80.50"),
                    )
                    coEvery {
                        budstikkaPublisher.publishOppfolgingsplanCreated(any(), any(), any())
                    } throws RuntimeException("boom")

                    val uuid = service.createOppfolgingsplan(
                        narmesteLederFnr = "10987654321",
                        sykmeldt = defaultSykmeldt(),
                        createOppfolgingsplanRequest = defaultOppfolgingsplan(),
                    )

                    service.getPersistedOppfolgingsplanByUuid(uuid).uuid shouldBe uuid
                    TestDB.database.findEventId(uuid).shouldNotBeNull()
                    coVerify(exactly = 1) {
                        budstikkaPublisher.publishOppfolgingsplanCreated(
                            oppfolgingsplanUuid = uuid,
                            sykmeldtFnr = "12345678901",
                            eventId = any(),
                        )
                    }
                }

                it("should rethrow cancellation exception from aareg") {
                    val aaregService = mockk<AaregService>()
                    val service = OppfolgingsplanService(
                        database = TestDB.database,
                        pdlService = mockk(relaxed = true),
                        esyfovarselProducer = mockk(relaxed = true),
                        budstikkaPublisher = mockk(relaxed = true),
                        aaregService = aaregService,
                    )
                    coEvery {
                        aaregService.getStillingsinformasjon("12345678901", "orgnummer")
                    } throws CancellationException("cancelled")

                    shouldThrow<CancellationException> {
                        service.createOppfolgingsplan(
                            narmesteLederFnr = "10987654321",
                            sykmeldt = defaultSykmeldt(),
                            createOppfolgingsplanRequest = defaultOppfolgingsplan(),
                        )
                    }
                }
            }

            describe("deleteExpiredOppfolgingsplanUtkast") {
                afterTest {
                    TestDB.clearAllData()
                    clearAllMocks(currentThreadOnly = true)
                }

                it("should delete drafts older than cutoff and keep exact cutoff and newer drafts") {
                    val referenceTime = ZonedDateTime.of(2025, 8, 15, 12, 0, 0, 0, ZoneOffset.UTC)
                    val cutoff = referenceTime.minusMonths(4).toInstant()

                    val expiredDraft = defaultPersistedOppfolgingsplanUtkast().copy(
                        narmesteLederId = "leder-expired",
                    )
                    val exactCutoffDraft = defaultPersistedOppfolgingsplanUtkast().copy(
                        narmesteLederId = "leder-cutoff",
                    )
                    val freshDraft = defaultPersistedOppfolgingsplanUtkast().copy(
                        narmesteLederId = "leder-fresh",
                    )

                    TestDB.database.persistOppfolgingsplanUtkast(expiredDraft)
                    TestDB.database.persistOppfolgingsplanUtkast(exactCutoffDraft)
                    TestDB.database.persistOppfolgingsplanUtkast(freshDraft)

                    TestDB.database.setOppfolgingsplanUtkastUpdatedAt(
                        expiredDraft.uuid,
                        cutoff.minus(1, ChronoUnit.DAYS)
                    )
                    TestDB.database.setOppfolgingsplanUtkastUpdatedAt(exactCutoffDraft.uuid, cutoff)
                    TestDB.database.setOppfolgingsplanUtkastUpdatedAt(
                        freshDraft.uuid,
                        referenceTime.minusMonths(3).minusDays(29).toInstant(),
                    )

                    val deletedDrafts = TestDB.database.deleteExpiredOppfolgingsplanUtkast(
                        retentionCutoff = cutoff,
                    )

                    deletedDrafts shouldBe 1
                    TestDB.database.findOppfolgingsplanUtkastByNarmesteLederId(expiredDraft.narmesteLederId)
                        .shouldBeNull()
                    TestDB.database.findOppfolgingsplanUtkastByNarmesteLederId(exactCutoffDraft.narmesteLederId)
                        ?.uuid shouldBe exactCutoffDraft.uuid
                    TestDB.database.findOppfolgingsplanUtkastByNarmesteLederId(freshDraft.narmesteLederId)
                        ?.uuid shouldBe freshDraft.uuid
                }

                it("should calculate retention cutoff from retentionMonths before deleting expired drafts") {
                    val service = OppfolgingsplanService(
                        database = TestDB.database,
                        pdlService = mockk(relaxed = true),
                        esyfovarselProducer = mockk(relaxed = true),
                        budstikkaPublisher = mockk(relaxed = true),
                        aaregService = mockk(relaxed = true),
                    )
                    val expiredDraft = defaultPersistedOppfolgingsplanUtkast().copy(
                        narmesteLederId = "leder-expired-default-path",
                    )
                    val freshDraft = defaultPersistedOppfolgingsplanUtkast().copy(
                        narmesteLederId = "leder-fresh-default-path",
                    )

                    TestDB.database.persistOppfolgingsplanUtkast(expiredDraft)
                    TestDB.database.persistOppfolgingsplanUtkast(freshDraft)

                    TestDB.database.setOppfolgingsplanUtkastUpdatedAt(
                        expiredDraft.uuid,
                        Instant.now().minus(180, ChronoUnit.DAYS),
                    )
                    TestDB.database.setOppfolgingsplanUtkastUpdatedAt(
                        freshDraft.uuid,
                        Instant.now().minus(30, ChronoUnit.DAYS),
                    )

                    val deletedDrafts = service.deleteExpiredOppfolgingsplanUtkast()

                    deletedDrafts shouldBe 1
                    TestDB.database.findOppfolgingsplanUtkastByNarmesteLederId(expiredDraft.narmesteLederId)
                        .shouldBeNull()
                    TestDB.database.findOppfolgingsplanUtkastByNarmesteLederId(freshDraft.narmesteLederId)
                        ?.uuid shouldBe freshDraft.uuid
                }
            }

            it("Should set varsel_published_at when varsel is sent") {
                val aaregService = mockk<AaregService>()
                val esyfovarselProducer = mockk<EsyfovarselProducer>(relaxed = true)
                val budstikkaPublisher = mockk<BudstikkaPublisher>()
                val service = OppfolgingsplanService(
                    database = TestDB.database,
                    pdlService = mockk(relaxed = true),
                    esyfovarselProducer = esyfovarselProducer,
                    budstikkaPublisher = budstikkaPublisher,
                    aaregService = aaregService,
                )
                coEvery {
                    aaregService.getStillingsinformasjon("12845678901", "orgnummer")
                } returns Stillingsinformasjon(
                    stillingstittel = "Systemutvikler",
                    stillingsprosent = BigDecimal("80.50"),
                )
                coEvery {
                    budstikkaPublisher.publishOppfolgingsplanCreated(any(), any(), any())
                } answers { thirdArg<UUID>() }

                val uuid = service.createOppfolgingsplan(
                    narmesteLederFnr = "10987654321",
                    sykmeldt = defaultSykmeldt(),
                    createOppfolgingsplanRequest = defaultOppfolgingsplan(),
                )

                TestDB.database.findVarselPublishedAtByOppfolgingsplanId(uuid).shouldNotBeNull()
            }

            it("Should not set varsel_published_at when varsel is not sent") {
                val aaregService = mockk<AaregService>()
                val esyfovarselProducer = mockk<EsyfovarselProducer>(relaxed = true)
                val budstikkaPublisher = mockk<BudstikkaPublisher>()
                val service = OppfolgingsplanService(
                    database = TestDB.database,
                    pdlService = mockk(relaxed = true),
                    esyfovarselProducer = esyfovarselProducer,
                    budstikkaPublisher = budstikkaPublisher,
                    aaregService = aaregService,
                )
                coEvery {
                    aaregService.getStillingsinformasjon("12845678901", "orgnummer")
                } returns Stillingsinformasjon(
                    stillingstittel = "Systemutvikler",
                    stillingsprosent = BigDecimal("80.50"),
                )
                coEvery {
                    budstikkaPublisher.publishOppfolgingsplanCreated(any(), any(), any())
                } throws RuntimeException("boom")

                val uuid = service.createOppfolgingsplan(
                    narmesteLederFnr = "10987654321",
                    sykmeldt = defaultSykmeldt(),
                    createOppfolgingsplanRequest = defaultOppfolgingsplan(),
                )

                TestDB.database.findVarselPublishedAtByOppfolgingsplanId(uuid).shouldBeNull()
            }
        }
    })
