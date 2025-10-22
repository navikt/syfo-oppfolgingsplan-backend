package no.nav.syfo.arkivporten.client

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import no.nav.syfo.application.exception.InternalServerErrorException
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.util.logger
import org.slf4j.LoggerFactory

interface IArkivportenClient {
    suspend fun publishOppfolginsplan(document: Document)
}

class FakeArkivportenClient : IArkivportenClient {
    val logger = logger()
    override suspend fun publishOppfolginsplan(document: Document) {
        logger.info(
            "Publishing Oppfolginsplan: ${document.documentId}, ${document.dialogTitle}, ${document.dialogSummary}"
        )
    }
}

class ArkivportenClient(
    private val arkivportenBaseUrl: String,
    private val texasHttpClient: TexasHttpClient,
    private val scope: String,
    private val httpClient: HttpClient,
) : IArkivportenClient {
    private val logger = LoggerFactory.getLogger(ArkivportenClient::class.qualifiedName)

    override suspend fun publishOppfolginsplan(document: Document) {
        logger.info("Publishing document to Arkivporten: ${document.documentId}, ${document.dialogTitle}")
        val token = try {
            texasHttpClient.systemToken(IDENTITY_PROVIDER, TexasHttpClient.getTarget(scope)).accessToken
        } catch (e: ClientRequestException) {
            logger.error("Error while requesting systemToken", e)
            throw InternalServerErrorException("Error while requesting systemToken")
        }
        val requestUrl = arkivportenBaseUrl + ARKIVPORTEN_DOCUMENT_PATH
        logger.info("Sending document to Arkivporten at $requestUrl")
        try {
            httpClient.post(requestUrl) {
                headers {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    append(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(document)
            }
        } catch (e: ClientRequestException) {
            logger.error("Error sending request to Arkivporten: ${e.response.bodyAsText()}")
            throw e
        }
    }

    companion object {
        const val IDENTITY_PROVIDER = "azuread"
        const val ARKIVPORTEN_DOCUMENT_PATH = "/internal/api/v1/documents"
    }
}
