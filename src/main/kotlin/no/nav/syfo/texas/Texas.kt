package no.nav.syfo.texas

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.nav.syfo.application.client.defaultHttpClientClient

@Serializable
data class TexasIntrospectionRequest(
    @SerialName("identity_provider")
    val identityProvider: String,
    val token: String,
)

@Serializable
data class TexasIntrospectionResponse(
    val active: Boolean,
    val error: String? = null,
    val scope: String? = null,
    val clientId: String? = null,
    val sub: String? = null,
    val aud: List<String>? = null,
    val iss: String? = null,
    val iat: Long? = null,
    val exp: Long? = null,
)

class TexasHttpClient(val environment: TexasEnvironment) {
    var client = defaultHttpClientClient()

    suspend fun introspectToken(identityProvider: String, token: String): TexasIntrospectionResponse {
        return client.use { client ->
             client.post(environment.tokenIntrospectionEndpoint) {
                 headers {
                     append("Content-Type", "application/json")
                 }
                 setBody(TexasIntrospectionRequest(
                     identityProvider = identityProvider,
                     token = token
                 ))
             }.body<TexasIntrospectionResponse>()
        }
    }
}

class TexasAuthPluginConfiguration(
    var environment: TexasEnvironment? = null,
)

val TexasAuthPlugin = createRouteScopedPlugin(
    name = "TexasAuthPlugin",
    createConfiguration = ::TexasAuthPluginConfiguration,
) {
    val environment = pluginConfig.environment ?: throw IllegalArgumentException("TexasAuthPlugin: environment must be set")
    val texasHttpClient = TexasHttpClient(environment)

    onCall { call ->
        val authorizationHeader = call.request.headers["Authorization"]
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            println("Missing bearer token")
            call.respondNullable(HttpStatusCode.Unauthorized)
            return@onCall
        }

        val token = authorizationHeader.removePrefix("Bearer ")
        val introspectionResponse = try {
            texasHttpClient.introspectToken("tokenx", token)
        } catch (e: Exception) {
            println("Failed to introspect token: ${e.message}")
            call.respondNullable(HttpStatusCode.Unauthorized)
            return@onCall
        }

        println(introspectionResponse)

        if (!introspectionResponse.active) {
            println("Token is not active")
            call.respondNullable(HttpStatusCode.Unauthorized)
            return@onCall
        }

        if (introspectionResponse.sub == null) {
            println("Token introspection response does not contain 'sub'")
            call.respondNullable(HttpStatusCode.Unauthorized)
            return@onCall
        }
        call.authentication.principal( introspectionResponse.sub)
    }
    println("TexasAuthPlugin installed")
}