package no.nav.syfo.pdfgen

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import no.nav.syfo.oppfolgingsplan.db.PersistedOppfolgingsplan
import no.nav.syfo.util.logger
import java.time.ZoneId

class PdfGenService(
    private val pdfGenClient: PdfGenClient
) {
    val logger = logger()
    suspend fun generatePdf(persistedOppfolgingsplan: PersistedOppfolgingsplan): ByteArray? = try {
        pdfGenClient.generatePdf(persistedOppfolgingsplan.toOppfolginsplanPdfV1())
    } catch (clientRequestException: ClientRequestException) {
        logger.error("Could not generate pdf for id ${persistedOppfolgingsplan.uuid}")
        throw RuntimeException("Error while generating pdf", clientRequestException)
    } catch (serverResponseException: ServerResponseException) {
        throw RuntimeException("Error while generating pdf", serverResponseException)
    }
}

fun PersistedOppfolgingsplan.toOppfolginsplanPdfV1(): OppfolginsplanPdfV1 = OppfolginsplanPdfV1(
    oppfolgingsplan = Oppfolginsplan(
        createdDate = this.createdAt.atZone(ZoneId.of("Europe/Oslo")).toLocalDate(),
        evaluationDate = this.sluttdato,
        sykmeldtName = this.sykmeldtFullName ?: "Sykmeldt Navn",
        sykmeldtFnr = this.sykmeldtFnr,
        organisasjonsnavn = this.organisasjonsnavn ?: "Eksempel AS",
        organisasjonsnummer = this.organisasjonsnummer,
        narmesteLederName = this.narmesteLederFullName ?: "Nærmeste Leder",
        sections = listOf(
            Section(
                id = "tilpassing",
                title = "Tilpasning av arbeidsoppgaver",
                description = "I denne delen av planen er det fint å kartlegge hvilke tilpasninger som er muligheter å gjøre i arbeidsoppgaver.",
                textInputFields = listOf(
                    TextInputField(
                        id = "forsøktHittil",
                        title = "Hva har dere forsøkt så langt i sykefraværet?",
                        description = "Beskriv hva dere har forsøkt av tilrettelegging så langt i sykefraværet. Hva har fungert, og hva har ikke fungert?",
                        value = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Ut nec leo porta, rhoncus sem vel, molestie odio. Nunc dictum maximus diam, eu commodo elit ullamcorper eu. Nam hendrerit scelerisque hendrerit. Integer neque orci, finibus id elementum iaculis, posuere ut neque. Ut tristique eros ac mollis vestibulum. Mauris bibendum, neque et posuere blandit, velit urna dapibus ex, sed sagittis nunc lorem in felis. Donec sit amet mattis nunc, non venenatis arcu. Integer odio purus, gravida facilisis nibh ac, gravida porta dui. Curabitur a leo erat. Sed leo metus, interdum et nisi eu, rhoncus finibus mi. Vestibulum porttitor luctus congue. Pellentesque mollis consectetur neque, vel ultricies felis hendrerit vel. Ut aliquam fringilla ex, id hendrerit est ullamcorper id"
                    ), TextInputField(
                        id = "hvordanTilrettelegge",
                        title = "Hvordan skal dere tilrettelegge arbeidshverdagen fremover?",
                        description = "Beskriv hva dere skal gjøre for at arbeidstakeren kan være i noe jobb",
                        value = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Ut nec leo porta, rhoncus sem vel, molestie odio. Nunc dictum maximus diam, eu commodo elit ullamcorper eu. Nam hendrerit scelerisque hendrerit. Integer neque orci, finibus id elementum iaculis, posuere ut neque. Ut tristique eros ac mollis vestibulum. Mauris bibendum, neque et posuere blandit, velit urna dapibus ex, sed sagittis nunc lorem in felis. Donec sit amet mattis nunc, non venenatis arcu. Integer odio purus, gravida facilisis nibh ac, gravida porta dui. Curabitur a leo erat. Sed leo metus, interdum et nisi eu, rhoncus finibus mi. Vestibulum porttitor luctus congue. Pellentesque mollis consectetur neque, vel ultricies felis hendrerit vel. Ut aliquam fringilla ex, id hendrerit est ullamcorper id"
                    )
                )
            )
        )
    )
)
