package no.nav.syfo.dokumentporten.client

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import no.nav.syfo.application.exception.ApiErrorException
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.util.logger
import org.slf4j.LoggerFactory

interface IDokumentportenClient {
    suspend fun publishOppfolgingsplan(document: Document)
}

class FakeDokumentportenClient : IDokumentportenClient {
    val logger = logger()
    override suspend fun publishOppfolgingsplan(document: Document) {
        logger.info(
            "Publishing Oppfolgingsplan: ${document.documentId}, ${document.title}, ${document.summary}"
        )
    }
}

class DokumentportenClient(
    private val dokumentportenBaseUrl: String,
    private val texasHttpClient: TexasHttpClient,
    private val scope: String,
    private val httpClient: HttpClient,
) : IDokumentportenClient {
    private val logger = LoggerFactory.getLogger(DokumentportenClient::class.qualifiedName)

    override suspend fun publishOppfolgingsplan(document: Document) {
        logger.info("Publishing document to Dokumentporten: ${document.documentId}")
        val token = try {
            texasHttpClient.systemToken(IDENTITY_PROVIDER, TexasHttpClient.getTarget(scope)).accessToken
        } catch (e: ClientRequestException) {
            logger.error("Error while requesting systemToken", e)
            throw ApiErrorException.InternalServerError("Error while requesting systemToken")
        }
        val requestUrl = dokumentportenBaseUrl + DOKUMENTPORTEN_DOCUMENT_PATH
        logger.info("Sending document to Dokumentporten at $requestUrl")
        try {
            httpClient.post(requestUrl) {
                headers {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    append(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(document)
            }
        } catch (e: ClientRequestException) {
            logger.error("Error sending request to Dokumentporten: ${e.response.bodyAsText()}")
            throw e
        }
    }

    companion object {
        const val IDENTITY_PROVIDER = "azuread"
        const val DOKUMENTPORTEN_DOCUMENT_PATH = "/internal/api/v1/documents"
    }
}
