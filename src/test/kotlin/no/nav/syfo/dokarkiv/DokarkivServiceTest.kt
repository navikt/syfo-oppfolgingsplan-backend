package no.nav.syfo.dokarkiv

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.UUID
import no.nav.syfo.defaultPersistedOppfolgingsplan
import no.nav.syfo.dokarkiv.client.DokarkivClient

class DokarkivServiceTest : DescribeSpec({
    val dokarkivClient = mockk<DokarkivClient>()
    val dokarkivService = DokarkivService(dokarkivClient)

    beforeTest {
        clearAllMocks()
    }

    describe("DokarkivService") {
        it("arkiverOppfolginsplan should call dokarkivClient with correct parameters") {
            // Arrange
            val oppfolginsplan = defaultPersistedOppfolgingsplan()
            val pdf = ByteArray(0) // Mock PDF content
            val expectedJournalpostId = UUID.randomUUID().toString()
            coEvery { dokarkivClient.sendJournalpostRequestToDokarkiv(any()) } returns expectedJournalpostId

            // Act
            val journalpostId = dokarkivService.arkiverOppfolginsplan(oppfolginsplan, pdf)

            // Assert
            journalpostId shouldBe expectedJournalpostId
            coVerify(exactly = 1) {
                dokarkivClient.sendJournalpostRequestToDokarkiv(
                    withArg {
                        it.tittel shouldBe "Oppfølgingsplan ${oppfolginsplan.organisasjonsnavn}"
                        it.kanal shouldBe DokarkivService.KANAL
                        it.journalpostType shouldBe DokarkivService.JOURNALPOST_TYPE
                        it.eksternReferanseId shouldBe oppfolginsplan.uuid.toString()
                        it.dokumenter.size shouldBe 1
                        it.dokumenter.first().tittel shouldBe "Oppfølgingsplan ${oppfolginsplan.organisasjonsnavn}"

                        it.bruker.id shouldBe oppfolginsplan.sykmeldtFnr
                        it.bruker.idType shouldBe DokarkivService.FNR_TYPE

                        it.dokumenter.first().dokumentvarianter.first().fysiskDokument shouldBe pdf

                        it.avsenderMottaker.navn shouldBe oppfolginsplan.organisasjonsnavn
                        it.avsenderMottaker.id shouldBe oppfolginsplan.organisasjonsnummer
                        it.avsenderMottaker.idType shouldBe DokarkivService.ID_TYPE_ORGNR
                    }
                )
            }
        }
    }
})
