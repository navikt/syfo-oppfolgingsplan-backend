package no.nav.syfo.arkivporten

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import no.nav.syfo.TestDB
import no.nav.syfo.arkivporten.client.FakeArkivportenClient
import no.nav.syfo.defaultPersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.db.PersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanserForArkivportenPublisering
import no.nav.syfo.oppfolgingsplan.db.setNarmesteLederFullName
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.pdfgen.PdfGenService
import no.nav.syfo.pdl.PdlService
import no.nav.syfo.persistOppfolgingsplan
import no.nav.syfo.varsel.EsyfovarselProducer

class ArkivportenServiceTest : DescribeSpec({
    val client = FakeArkivportenClient()
    val clientSpy = spyk(client)
    val pdfGenService = mockk<PdfGenService>()
    val testDb = TestDB.database
    val oppfolginsplanService = OppfolgingsplanService(
        database = testDb,
        pdlService = mockk<PdlService>(relaxed = true),
        esyfovarselProducer = mockk<EsyfovarselProducer>(relaxed = true),
    )

    beforeTest {
        clearAllMocks()
        TestDB.clearAllData()
    }
    fun addDefaultOppfolgingsplanerToDb(): PersistedOppfolgingsplan =
        defaultPersistedOppfolgingsplan().let {
            val uuid = testDb.persistOppfolgingsplan(it)
            testDb.setNarmesteLederFullName(uuid, it.narmesteLederFullName ?: "Narmeste Leder")
            it.copy(uuid = uuid)
        }

    describe("ArkivportenService") {
        // Arrange
        it("Fetches unsendt plans and sends them to Arkivporten") {
            // Arrange
            coEvery { pdfGenService.generatePdf(any()) } returns "PDF".toByteArray()
            addDefaultOppfolgingsplanerToDb()
            addDefaultOppfolgingsplanerToDb()
            val service = ArkivportenService(
                arkivportenClient = clientSpy,
                database = testDb,
                pdfGenService = pdfGenService,
                oppfolgingsplanService = oppfolginsplanService,
            )
            // Act
            val unsendtPlans = testDb.findOppfolgingsplanserForArkivportenPublisering()
            service.findAndSendOppfolgingsplaner()

            // Assert
            coVerify(exactly = 2) {
                clientSpy.publishOppfolginsplan(any())
            }
            coVerify(exactly = 1) {
                pdfGenService.generatePdf(eq(unsendtPlans.first()))
                pdfGenService.generatePdf(eq(unsendtPlans.last()))
            }
            // Should no longer be any unsent plans
            testDb.findOppfolgingsplanserForArkivportenPublisering().size shouldBe 0
        }
    }
})
