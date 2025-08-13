package no.nav.syfo.dokarkiv

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.append
import io.ktor.http.headers
import java.util.Random
import kotlinx.coroutines.runBlocking
import net.datafaker.Faker
import no.nav.syfo.dokarkiv.domain.JournalpostRequest
import no.nav.syfo.dokarkiv.domain.JournalpostResponse
import no.nav.syfo.texas.client.TexasHttpClient
import org.slf4j.LoggerFactory

interface IDokarkivClient {
    suspend fun sendJournalpostRequestToDokarkiv(journalpostRequest: JournalpostRequest): String
}

class FakeDokarkivClient : IDokarkivClient {
    val faker = Faker(Random(System.currentTimeMillis()))
    override suspend fun sendJournalpostRequestToDokarkiv(journalpostRequest: JournalpostRequest): String {
        return faker.numerify("##########")
    }
}

class DokarkivClient(
    private val dokarkivBaseUrl: String,
    private val texasHttpClient: TexasHttpClient,
    private val scope: String,
    private val httpClient: HttpClient,
) : IDokarkivClient {
    private val log = LoggerFactory.getLogger(DokarkivClient::class.qualifiedName)

    @Suppress("ThrowsCount")
    override suspend fun sendJournalpostRequestToDokarkiv(journalpostRequest: JournalpostRequest): String {
        val token = texasHttpClient.systemToken(IDENTITY_PROVIDER, TexasHttpClient.getTarget(scope)).accessToken
        val requestUrl = dokarkivBaseUrl + JOURNALPOST_API_PATH
        val response = try {
            httpClient.post(requestUrl) {
                headers {
                    append(HttpHeaders.ContentType, ContentType.Application.Json)
                    append(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(journalpostRequest)
            }
        } catch (e: Exception) {
            log.error("Could not send oppflgingsplan to dokarkiv", e)
            throw e
        }

        val responseBody = when (response.status) {
            HttpStatusCode.Created -> {
                runBlocking {
                    response.body<JournalpostResponse>()
                }
            }

            HttpStatusCode.Conflict -> {
                log.warn("Journalpost for oppfolginsplan plan already created!")
                runBlocking {
                    response.body<JournalpostResponse>()
                }
            }

            else -> {
                log.error("Call to Dokarkiv failed with status: ${response.status}, : ${response.bodyAsText()}")
                throw RuntimeException("Internal server error")
            }
        }

        if (!responseBody.journalpostferdigstilt) {
            log.warn("Journalpost is not ferdigstilt with message " + responseBody.melding)
        }
        return responseBody.journalpostId.toString()
    }

    companion object {
        const val IDENTITY_PROVIDER = "azuread"
        const val JOURNALPOST_API_PATH = "/rest/journalpostapi/v1/journalpost?forsoekFerdigstill=true"
    }
}
