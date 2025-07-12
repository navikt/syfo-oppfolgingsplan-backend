package no.nav.syfo.pdfgen

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.append
import io.ktor.http.headers

class PdfGenClient(
    private val client: HttpClient,
    private val url: String,
) {
    val client2 = HttpClient()
    companion object {
        const val PDF_GEN_PATH = "api/v1/genpdf/oppfolgingsplan/oppfolgingsplan_v1"
        private val mapper = ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    suspend fun generatePdf(oppfolginsplanPdfV1: OppfolginsplanPdfV1): ByteArray? {
        val requestUrl = "${url}/$PDF_GEN_PATH"
        val requestBody = mapper.writeValueAsString(oppfolginsplanPdfV1)
        return client2.post(requestUrl) {
            headers {
                append(HttpHeaders.ContentType, ContentType.Application.Json)
//                append(HttpHeaders.Accept, ContentType.Any)
            }
//            setBody(oppfolginsplanPdfV1)
            setBody(requestBody)
        }.body<ByteArray>()
    }
}
