package no.nav.syfo.texas

import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.authentication
import no.nav.syfo.application.auth.BrukerPrincipal
import no.nav.syfo.application.exception.ApiErrorException
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
                throw ApiErrorException.Unauthorized("No bearer token found in request")
            }

            val introspectionResponse = try {
                client?.introspectToken("azuread", bearerToken)
                    ?: error("TexasHttpClient is not configured")
            } catch (e: Exception) {
                call.application.environment.log.error("Failed to introspect token: ${e.message}", e)
                throw ApiErrorException.Unauthorized("Failed to introspect token", e)
            }

            if (!introspectionResponse.active) {
                call.application.environment.log.warn(
                    "Token is not active: ${introspectionResponse.error ?: "No error message"}"
                )
                throw ApiErrorException.Unauthorized("Token is not active")
            }
            if (introspectionResponse.NAVident == null) {
                call.application.environment.log.warn("No NAVident in token claims")
                throw ApiErrorException.Unauthorized("No NAVident in token claims")
            }
            call.authentication.principal(
                BrukerPrincipal(
                    ident = introspectionResponse.NAVident,
                    token = bearerToken,
                    clientId = introspectionResponse.azpName
                )
            )
        }
    }
    logger.info("TexasAzureAdAuthPlugin installed")
}
