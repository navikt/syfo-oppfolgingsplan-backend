package no.nav.syfo.texas

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.authentication
import io.ktor.server.response.respondNullable
import no.nav.syfo.application.auth.VeilderPrincipal
import no.nav.syfo.util.logger

private val logger = logger("no.nav.syfo.texas.TexasAzureAdAuthPlugin")

val TexasAzureADAuthPlugin = createRouteScopedPlugin(
    name = "TexasAzureAdAuthPlugin",
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
                client?.introspectToken("azuread", bearerToken)
                    ?: error("TexasHttpClient is not configured")
            } catch (e: Exception) {
                call.application.environment.log.error("Failed to introspect token: ${e.message}", e)
                call.respondNullable(HttpStatusCode.Unauthorized)
                return@onCall
            }

            if (!introspectionResponse.active) {
                call.application.environment.log.warn(
                    "Token is not active: ${introspectionResponse.error ?: "No error message"}"
                )
                call.respondNullable(HttpStatusCode.Unauthorized)
                return@onCall
            }
            if (introspectionResponse.sub == null) {
                call.application.environment.log.warn("No sub in token claims")
                call.respondNullable(HttpStatusCode.Unauthorized)
                return@onCall
            }
            call.authentication.principal(VeilderPrincipal(introspectionResponse.sub, bearerToken))
        }
    }
    logger.info("TexasAzureAdAuthPlugin installed")
}
