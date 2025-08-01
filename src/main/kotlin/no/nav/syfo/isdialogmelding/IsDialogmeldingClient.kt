package no.nav.syfo.isdialogmelding

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import no.nav.syfo.application.exception.LegeNotFoundException
import no.nav.syfo.util.logger

class IsDialogmeldingClient(
    private val httpClient: HttpClient,
    private val isDialogmeldingBaseUrl: String,
) {
    private val logger = logger()

    suspend fun sendOppfolgingsplanToGeneralPractitioner(
        token: String,
        sykmeldtFnr: String,
        plansAsPdf: ByteArray,
    ) {
        try {
            httpClient.post("$isDialogmeldingBaseUrl/api/person/v1/oppfolgingsplan") {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                header("Authorization", "Bearer $token")
                setBody(
                    OppfolgingsplanDialogmelding(
                        sykmeldtFnr = sykmeldtFnr,
                        oppfolgingsplanPdf = plansAsPdf,
                    )
                )
            }
        } catch (clientRequestException: ClientRequestException) {
            when (clientRequestException.response.status) {
                HttpStatusCode.NotFound -> {
                    logger.warn("Unable to determine fastlege, or lacking appropiate 'partnerinformasjon'-data")
                    throw LegeNotFoundException(
                        "Unable to determine fastlege, or lacking appropiate 'partnerinformasjon'-data",
                    )
                }
                else -> {
                    logger.error("Call to to send oppfolgingsplan to fastlege failed with status: " +
                            "${clientRequestException.response.status}, response body: ${clientRequestException.response.bodyAsText()}")
                    throw RuntimeException("Error while sending oppfolgingsplan to general practitioner", clientRequestException)
                }
            }
        }
    }
}
