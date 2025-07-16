package no.nav.syfo.pdfgen

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.append

class PdfGenClient(
    private val client: HttpClient,
    private val url: String,
) {
    companion object {
        const val PDF_GEN_PATH = "api/v1/genpdf/oppfolgingsplan/oppfolgingsplan_v1"
    }

    suspend fun generatePdf(oppfolginsplanPdfV1: OppfolginsplanPdfV1): ByteArray? {
        val requestUrl = "${url}/$PDF_GEN_PATH"
        val response = client.post(requestUrl) {
            headers {
                append(HttpHeaders.ContentType, ContentType.Application.Json)
            }
            setBody(oppfolginsplanPdfV1)
        }
        return when (response.status) {
            HttpStatusCode.OK -> {
                response.body<ByteArray>()
            }

            else -> null
        }
    }
}
