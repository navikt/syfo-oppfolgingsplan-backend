package no.nav.syfo.isdialogmelding

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import no.nav.syfo.util.logger

class IsDialogmeldingService(
    private val client: IsDialogmeldingClient,
) {
    private val logger = logger()

    suspend fun sendLpsPlanToGeneralPractitioner(
        token: String,
        sykmeldtFnr: String,
        plansAsPdf: ByteArray,
    ) {
        try {
            client.sendLpsPlanToGeneralPractitioner(
                token = token,
                sykmeldtFnr = sykmeldtFnr,
                plansAsPdf = plansAsPdf,
            )
        } catch (clientRequestException: ClientRequestException) {
            when (clientRequestException.response.status) {
                HttpStatusCode.NotFound -> {
                    logger.warn("Feil ved oppslag av fastlege eller partnerinformasjon")
                }
                else -> throw RuntimeException("Error while sending oppfolgingsplan to general practitioner", clientRequestException)
            }
        }
    }
}
