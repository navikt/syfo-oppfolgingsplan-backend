package no.nav.syfo.oppfolgingsplan.service

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import java.time.temporal.ChronoUnit
import no.nav.syfo.TestDB
import no.nav.syfo.defaultPersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.dto.mapToOppfolgingsplanMetadata
import no.nav.syfo.pdl.PdlService
import no.nav.syfo.persistOppfolgingsplan
import no.nav.syfo.varsel.EsyfovarselProducer

class OppfolginsplanServiceTest : DescribeSpec({
    describe("Extension function tests") {
        it("toListOppfolginsplanVeiler should filter out items not delt with veileder") {
            // Arrange
            val deltMedVeilder = defaultPersistedOppfolgingsplan().mapToOppfolgingsplanMetadata().copy(
                deltMedVeilederTidspunkt = Instant.now().plus(10, ChronoUnit.MINUTES),
                skalDelesMedVeileder = true
            )
            val ikkeDeltMedVeilder = defaultPersistedOppfolgingsplan().mapToOppfolgingsplanMetadata().copy(
                deltMedVeilederTidspunkt = null,
                skalDelesMedVeileder = false
            )
            val oppfolgingsplaner = listOf(ikkeDeltMedVeilder, deltMedVeilder)

            // Act
            val filtered = oppfolgingsplaner.toListOppfolginsplanVeiler()
            // Assert

            filtered.size shouldBe 1
            filtered.first().uuid shouldBe deltMedVeilder.uuid
        }
    }
    describe("public function tests") {
        describe("getAndSetNarmestelederFullname") {
            afterTest {
                TestDB.clearAllData()
                clearAllMocks()
            }
            it("should set narmesteleder fullname when it is missing") {
                // Arrange
                val expectedFullname = "Ola Nordmann"
                val pdlServive = mockk<PdlService>()
                val service = OppfolgingsplanService(
                    database = TestDB.database,
                    pdlService = pdlServive,
                    esyfovarselProducer = mockk<EsyfovarselProducer>(relaxed = true),
                )
                coEvery { pdlServive.getNameFor(any()) } returns expectedFullname
                val plan = defaultPersistedOppfolgingsplan().copy(
                    narmesteLederFullName = null
                )
                val persistedPlan = TestDB.database.persistOppfolgingsplan(plan).let {
                    plan.copy(uuid = it)
                }

                // Act
                val result = service.getAndSetNarmestelederFullname(persistedPlan)

                // Assert
                result.narmesteLederFullName shouldBe "Ola Nordmann"
                val fromDb = service.getOppfolgingsplanByUuid(persistedPlan.uuid)
                fromDb?.narmesteLederFullName shouldBe "Ola Nordmann"
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
    }
})
