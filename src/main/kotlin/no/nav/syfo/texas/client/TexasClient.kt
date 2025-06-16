package no.nav.syfo.texas.client

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import no.nav.syfo.application.client.defaultHttpClient
import no.nav.syfo.texas.TexasEnvironment

class TexasHttpClient(val environment: TexasEnvironment) {

    suspend fun introspectToken(identityProvider: String, token: String): TexasIntrospectionResponse {
        return defaultHttpClient().use { client ->
            client.post(environment.tokenIntrospectionEndpoint) {
                contentType(ContentType.Application.Json)
                setBody(TexasIntrospectionRequest(
                    identityProvider = identityProvider,
                    token = token
                ))
            }.body<TexasIntrospectionResponse>()
        }
    }

    suspend fun exhangeTokenForDineSykmeldte(token: String): TexasExchangeResponse {
        return exchangeToken(environment.exchangeTargetDineSykmeldte, token)
    }

    private suspend fun exchangeToken(target: String, token: String): TexasExchangeResponse {
        return defaultHttpClient().use { client ->
            client.post(environment.tokenExchangeEndpoint) {
                contentType(ContentType.Application.Json)
                setBody(TexasExchangeRequest(
                    identityProvider = "tokenx",
                    target = target,
                    userToken = token
                ))
            }.body<TexasExchangeResponse>()
        }
    }
}