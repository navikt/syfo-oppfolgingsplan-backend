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
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanserForArkivportenPublisering
import no.nav.syfo.pdfgen.PdfGenService
import no.nav.syfo.persistOppfolgingsplan

class ArkivportenServiceTest : DescribeSpec({
    val client = FakeArkivportenClient()
    val clientSpy = spyk(client)
    val pdfgenService = mockk<PdfGenService>()
    val testDb = TestDB.database

    beforeTest {
        clearAllMocks()
        TestDB.clearAllData()
    }

    describe("ArkivportenService") {
        // Arrange
        it("Fetches unsendt plans and sends them to Arkivporten") {
            // Arrange
            coEvery { pdfgenService.generatePdf(any()) } returns "PDF".toByteArray()
            testDb.persistOppfolgingsplan(defaultPersistedOppfolgingsplan())
            testDb.persistOppfolgingsplan(defaultPersistedOppfolgingsplan())
            val service = ArkivportenService(
                arkivportenClient = clientSpy,
                database = testDb,
                pdfGenService = pdfgenService,
            )
            // Act
            val unsendtPlans = testDb.findOppfolgingsplanserForArkivportenPublisering()
            service.finddAndPublishOppfolgingsplaner()

            // Assert
            coVerify(exactly = 2) {
                clientSpy.publishOppfolginsplan(any())
            }
            coVerify(exactly = 1) {
                pdfgenService.generatePdf(eq(unsendtPlans.first()))
                pdfgenService.generatePdf(eq(unsendtPlans.last()))
            }
            // Should no longer be any unsent plans
            testDb.findOppfolgingsplanserForArkivportenPublisering().size shouldBe 0
        }
    }
})
