package no.nav.syfo.isdialogmelding

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.NotFoundException
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
                    logger.warn("Unable to determine fastlege, or lacking appropiate 'partnerinformasjon'-data")
                    throw NotFoundException(
                        "Unable to determine fastlege, or lacking appropiate 'partnerinformasjon'-data",
                    )
                }
                else -> {
                    logger.error("Call to to send LPS plan to fastlege failed with status: " +
                            "${clientRequestException.response.status}, response body: ${clientRequestException.response.bodyAsText()}")
                    throw RuntimeException("Error while sending oppfolgingsplan to general practitioner", clientRequestException)
                }
            }
        }
    }
}
