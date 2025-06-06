package no.nav.syfo.texas

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*


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