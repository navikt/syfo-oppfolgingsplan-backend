package no.nav.syfo.narmesteleder.client

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.util.configuredJacksonMapper
import java.util.UUID

private const val IDENTITY_PROVIDER = "azuread"
private const val INVALID_RESPONSE_MESSAGE = "Narmesteleder returned an invalid response"
const val NARMESTELEDER_LOOKUP_PATH = "/internal/api/v1/lookup"

interface INarmestelederClient {
    suspend fun findActiveNarmesteleder(
        sykmeldtFnr: String,
        organisasjonsnummer: String,
    ): Narmesteleder?
}

class FakeNarmestelederClient : INarmestelederClient {
    override suspend fun findActiveNarmesteleder(
        sykmeldtFnr: String,
        organisasjonsnummer: String,
    ): Narmesteleder? = null
}

class NarmestelederClient(
    private val httpClient: HttpClient,
    private val narmestelederBaseUrl: String,
    private val texasHttpClient: TexasHttpClient,
    private val scope: String,
) : INarmestelederClient {
    override suspend fun findActiveNarmesteleder(
        sykmeldtFnr: String,
        organisasjonsnummer: String,
    ): Narmesteleder? {
        val token = texasHttpClient.systemToken(IDENTITY_PROVIDER, scope).accessToken
        val responseBody = httpClient
            .post("$narmestelederBaseUrl$NARMESTELEDER_LOOKUP_PATH") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(
                    NarmestelederLookupRequest(
                        employeeNationalIdentificationNumber = sykmeldtFnr,
                        organizationNumber = organisasjonsnummer,
                    ),
                )
            }
            .bodyAsText()

        return try {
            configuredJacksonMapper.readValue<NarmestelederLookupResponse>(responseBody).lineManager
        } catch (_: JsonProcessingException) {
            throw IllegalStateException(INVALID_RESPONSE_MESSAGE)
        }
    }
}

data class NarmestelederLookupRequest(
    val employeeNationalIdentificationNumber: String,
    val organizationNumber: String,
)

data class NarmestelederLookupResponse(
    val lineManager: Narmesteleder?,
)

data class Narmesteleder(
    val id: UUID,
    val nationalIdentificationNumber: String,
    val emailAddresses: List<String>,
)
