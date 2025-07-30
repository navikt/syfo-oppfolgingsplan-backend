package no.nav.syfo.isdialogmelding

import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.append

class IsDialogmeldingClient(
    private val httpClient: HttpClient,
    private val isDialogmeldingBaseUrl: String,
) {
    suspend fun sendLpsPlanToGeneralPractitioner(
        token: String,
        sykmeldtFnr: String,
        plansAsPdf: ByteArray,
    ): HttpResponse {
        return httpClient.post("$isDialogmeldingBaseUrl/api/person/v1/oppfolgingsplan") {
            headers {
                append(HttpHeaders.ContentType, ContentType.Application.Json)
                append(HttpHeaders.Authorization, token)
            }
            setBody(
                OppfolgingsplanDialogmelding(
                    sykmeldtFnr = sykmeldtFnr,
                    oppfolgingsplanPdf = plansAsPdf,
                )
            )
        }
    }
}
