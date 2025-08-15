package no.nav.syfo.pdl.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import no.nav.syfo.pdl.client.model.GetPersonRequest
import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.client.model.GetPersonVariables
import org.intellij.lang.annotations.Language

private const val BEHANDLINGSNUMMER_DIGITAL_OPPFOLGINGSPLAN = "B275"
private const val PDL_BEHANDLINGSNUMMER_HEADER = "behandlingsnummer"

@Language("GraphQL")
private val getPersonQuery =
    """
    query(${'$'}ident: ID!){
      person: hentPerson(ident: ${'$'}ident) {
      	navn(historikk: false) {
      	  fornavn
      	  mellomnavn
      	  etternavn
        }
      }
      identer: hentIdenter(ident: ${'$'}ident, historikk: false) {
          identer {
            ident,
            gruppe
          }
        }
    }
"""
        .trimIndent()

class PdlClient(
    private val httpClient: HttpClient,
    private val pdlBaseUrl: String,
) {
    suspend fun getPerson(fnr: String, token: String): GetPersonResponse {
        val getPersonRequest =
            GetPersonRequest(
                query = getPersonQuery,
                variables = GetPersonVariables(ident = fnr),
            )

        return httpClient
            .post(pdlBaseUrl) {
                setBody(getPersonRequest)
                header(HttpHeaders.Authorization, "Bearer $token")
                header(PDL_BEHANDLINGSNUMMER_HEADER, BEHANDLINGSNUMMER_DIGITAL_OPPFOLGINGSPLAN)
                header(HttpHeaders.ContentType, "application/json")
            }
            .body()
    }
}
