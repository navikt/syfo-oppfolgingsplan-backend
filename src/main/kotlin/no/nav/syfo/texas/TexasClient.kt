package no.nav.syfo.texas

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import no.nav.syfo.application.client.defaultHttpClient

@Serializable
data class TexasIntrospectionRequest(
    @SerialName("identity_provider")
    val identityProvider: String,
    val token: String,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class TexasIntrospectionResponse(
    val active: Boolean,
    val error: String? = null,
    val pid: String? = null,
    val acr: String? = null,
    val aud: String? = null,
    val azp: String? = null,
    val exp: Long? = null,
    val iat: Long? = null,
    val iss: String? = null,
    val jti: String? = null,
    val nbf: Long? = null,
    val sub: String? = null,
    val tid: String? = null,
)

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
}