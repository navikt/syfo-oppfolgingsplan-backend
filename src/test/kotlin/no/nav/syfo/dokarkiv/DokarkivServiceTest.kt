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
        it("arkiverOppfolgingsplan should call dokarkivClient with correct parameters") {
            // Arrange
            val oppfolgingsplan = defaultPersistedOppfolgingsplan()
            val pdf = ByteArray(0) // Mock PDF content
            val expectedJournalpostId = UUID.randomUUID().toString()
            coEvery { dokarkivClient.sendJournalpostRequestToDokarkiv(any()) } returns expectedJournalpostId

            // Act
            val journalpostId = dokarkivService.arkiverOppfolgingsplan(oppfolgingsplan, pdf)

            // Assert
            journalpostId shouldBe expectedJournalpostId
            coVerify(exactly = 1) {
                dokarkivClient.sendJournalpostRequestToDokarkiv(
                    withArg {
                        it.tittel shouldBe "Oppfølgingsplan ${oppfolgingsplan.organisasjonsnavn}"
                        it.kanal shouldBe DokarkivService.KANAL
                        it.journalpostType shouldBe DokarkivService.JOURNALPOST_TYPE
                        it.eksternReferanseId shouldBe oppfolgingsplan.uuid.toString()
                        it.dokumenter.size shouldBe 1
                        it.dokumenter.first().tittel shouldBe "Oppfølgingsplan ${oppfolgingsplan.organisasjonsnavn}"

                        it.bruker.id shouldBe oppfolgingsplan.sykmeldtFnr
                        it.bruker.idType shouldBe DokarkivService.FNR_TYPE

                        it.dokumenter.first().dokumentvarianter.first().fysiskDokument shouldBe pdf

                        it.avsenderMottaker.navn shouldBe oppfolgingsplan.organisasjonsnavn
                        it.avsenderMottaker.id shouldBe oppfolgingsplan.organisasjonsnummer
                        it.avsenderMottaker.idType shouldBe DokarkivService.ID_TYPE_ORGNR
                    }
                )
            }
        }
    }
})
