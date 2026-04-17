package no.nav.syfo.aareg.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import no.nav.syfo.aareg.Arbeidsforhold
import no.nav.syfo.texas.client.TexasHttpClient

interface IAaregClient {
    suspend fun getArbeidsforhold(fnr: String): List<Arbeidsforhold>
}

class AaregClient(
    private val httpClient: HttpClient,
    private val aaregBaseUrl: String,
    private val texasHttpClient: TexasHttpClient,
    private val scope: String,
) : IAaregClient {
    override suspend fun getArbeidsforhold(fnr: String): List<Arbeidsforhold> {
        val token = texasHttpClient.systemToken(
            IDENTITY_PROVIDER,
            TexasHttpClient.getTarget(scope),
        ).accessToken

        return httpClient
            .get("$aaregBaseUrl$ARBEIDSFORHOLD_PATH") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(NAV_PERSONIDENT_HEADER, fnr)
                url {
                    parameters.append("regelverk", "A_ORDNINGEN")
                    parameters.append("arbeidsforholdstatus", "AKTIV,FREMTIDIG")
                    parameters.append("sporingsinformasjon", "false")
                }
            }
            .body()
    }

    companion object {
        const val ARBEIDSFORHOLD_PATH = "/api/v2/arbeidstaker/arbeidsforhold"
        const val NAV_PERSONIDENT_HEADER = "Nav-Personident"
        const val IDENTITY_PROVIDER = "azuread"
    }
}
