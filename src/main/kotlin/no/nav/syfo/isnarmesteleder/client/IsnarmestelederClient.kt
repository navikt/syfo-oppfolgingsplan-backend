package no.nav.syfo.isnarmesteleder.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.util.logger
import java.time.LocalDate

fun interface IIsnarmestelederClient {
    suspend fun getNarmesteLederRelasjoner(token: String): List<NarmesteLederRelasjonDTO>
}

class IsnarmestelederHttpClient(
    private val httpClient: HttpClient,
    private val texasHttpClient: TexasHttpClient,
    private val isnarmestelederBaseUrl: String,
) : IIsnarmestelederClient {
    private val logger = logger()

    override suspend fun getNarmesteLederRelasjoner(token: String): List<NarmesteLederRelasjonDTO> {
        val exchangedToken = try {
            texasHttpClient.exchangeTokenForIsnarmesteleder(token).accessToken
        } catch (e: ResponseException) {
            logger.error("Error while exchanging TokenX token for isnarmesteleder", e)
            throw RuntimeException("Error while exchanging TokenX token for isnarmesteleder", e)
        }

        return try {
            httpClient
                .get("$isnarmestelederBaseUrl$NARMESTELEDER_RELASJONER_PATH") {
                    header(HttpHeaders.Authorization, "Bearer $exchangedToken")
                }
                .body<List<NarmesteLederRelasjonDTO>>()
        } catch (e: ResponseException) {
            logger.error(
                "Error fetching nærmeste leder-relasjoner from isnarmesteleder: ${e.response.bodyAsText()}",
                e,
            )
            throw RuntimeException("Error while fetching nærmeste leder-relasjoner from isnarmesteleder", e)
        }
    }

    companion object {
        const val NARMESTELEDER_RELASJONER_PATH = "/api/selvbetjening/v1/narmestelederrelasjoner"
    }
}

class FakeIsnarmestelederClient : IIsnarmestelederClient {
    override suspend fun getNarmesteLederRelasjoner(token: String): List<NarmesteLederRelasjonDTO> = listOf(
        NarmesteLederRelasjonDTO(
            uuid = "11111111-1111-1111-1111-111111111111",
            virksomhetsnummer = "999888777",
            virksomhetsnavn = "Testbedrift AS",
            narmesteLederPersonIdentNumber = "12345678910",
            narmesteLederNavn = "Test Testesen",
            status = "INNMELDT_AKTIV",
            aktivFom = LocalDate.now().minusMonths(6),
            aktivTom = null,
        ),
        NarmesteLederRelasjonDTO(
            uuid = "22222222-2222-2222-2222-222222222222",
            virksomhetsnummer = "888777666",
            virksomhetsnavn = "Eksempelbedrift AS",
            narmesteLederPersonIdentNumber = "01987654321",
            narmesteLederNavn = "Leder Ledersen",
            status = "DEAKTIVERT",
            aktivFom = LocalDate.now().minusYears(1),
            aktivTom = LocalDate.now().minusMonths(1),
        ),
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class NarmesteLederRelasjonDTO(
    val uuid: String,
    val virksomhetsnummer: String,
    val virksomhetsnavn: String?,
    val narmesteLederPersonIdentNumber: String,
    val narmesteLederNavn: String?,
    val status: String,
    val aktivFom: LocalDate,
    val aktivTom: LocalDate?,
)
