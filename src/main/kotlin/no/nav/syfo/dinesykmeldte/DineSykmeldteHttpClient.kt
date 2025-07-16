package no.nav.syfo.dinesykmeldte

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

fun interface IDineSykmeldteHttpClient {
    suspend fun getSykmeldtForNarmesteLederId(
        narmestelederId: String,
        token: String,
    ): Sykmeldt
}

class DineSykmeldteHttpClient(
    private val httpClient: HttpClient,
    private val dineSykmeldteBaseUrl: String,
) : IDineSykmeldteHttpClient {
    override suspend fun getSykmeldtForNarmesteLederId(
        narmestelederId: String,
        token: String,
    ): Sykmeldt {
        return httpClient
            .get("$dineSykmeldteBaseUrl/api/v2/dinesykmeldte/$narmestelederId") {
                header("Authorization", "Bearer $token")
            }
            .body<Sykmeldt>()
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
