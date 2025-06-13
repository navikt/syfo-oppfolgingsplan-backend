package no.nav.syfo.dinesykmeldte

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import no.nav.syfo.application.client.defaultHttpClient

class DineSykmeldteHttpClient(
    private val dineSykmeldteBaseUrl: String,
) {
    suspend fun getSykmeldtForNarmesteLederId(
        narmestelederId: String,
        token: String,
    ): Sykmeldt? {
        return defaultHttpClient().use { client: HttpClient ->
            try {
                client
                    .get("$dineSykmeldteBaseUrl/api/v2/dinesykmeldte/$narmestelederId") {
                        header("Authorization", "Bearer $token")
                    }
                    .body<Sykmeldt>()
            } catch (clientRequestException: ClientRequestException) {
                when (clientRequestException.response.status) {
                    HttpStatusCode.NotFound -> null
                    else -> throw clientRequestException
                }
            }
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class Sykmeldt(
    val narmestelederId: String,
    val orgnummer: String,
    val fnr: String,
    val navn: String?,
    val aktivSykmelding: Boolean?,
)