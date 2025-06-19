package no.nav.syfo.texas

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.authentication
import io.ktor.server.request.authorization
import io.ktor.server.response.respondNullable
import no.nav.syfo.application.auth.BrukerPrincipal
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.util.logger

internal val logger = logger("no.nav.syfo.texas.TexasAuthPlugin")

class TexasAuthPluginConfiguration(
    var client: TexasHttpClient? = null,
)

val TexasAuthPlugin = createRouteScopedPlugin(
    name = "TexasAuthPlugin",
    createConfiguration = ::TexasAuthPluginConfiguration,
) {
    pluginConfig.apply {
        onCall { call ->
            val bearerToken = call.bearerToken()
            if (bearerToken == null) {
                call.application.environment.log.warn("No bearer token found in request")
                call.respondNullable(HttpStatusCode.Unauthorized)
                return@onCall
            }

            val introspectionResponse = try {
                client?.introspectToken("tokenx", bearerToken)
                    ?: throw IllegalStateException("TexasHttpClient is not configured")
            } catch (e: Exception) {

                call.application.environment.log.error("Failed to introspect token: ${e.message}", e)
                call.respondNullable(HttpStatusCode.Unauthorized)
                return@onCall
            }

            if (!introspectionResponse.active) {
                call.application.environment.log.warn("Token is not active: ${introspectionResponse.error ?: "No error message"}")
                call.respondNullable(HttpStatusCode.Unauthorized)
                return@onCall
            }
            if (!introspectionResponse.acr.equals("Level4", ignoreCase = true)) {
                call.application.environment.log.warn("User does not have Level4 access: ${introspectionResponse.acr}")
                call.respondNullable(HttpStatusCode.Forbidden)
                return@onCall
            }

            if (introspectionResponse.pid == null) {
                call.application.environment.log.warn("No pid in token claims")
                call.respondNullable(HttpStatusCode.Unauthorized)
                return@onCall
            }
            call.authentication.principal(BrukerPrincipal(introspectionResponse.pid, bearerToken))
        }
    }
    logger.info("TexasAuthPlugin installed")
}

fun ApplicationCall.bearerToken(): String? =
    request
        .authorization()
        ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
        ?.removePrefix("Bearer ")
        ?.removePrefix("bearer ")