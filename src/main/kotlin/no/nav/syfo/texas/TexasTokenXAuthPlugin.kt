package no.nav.syfo.texas

import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.authentication
import no.nav.syfo.application.auth.BrukerPrincipal
import no.nav.syfo.application.exception.ApiErrorException
import no.nav.syfo.application.exception.ErrorType
import no.nav.syfo.util.logger

private val logger = logger("no.nav.syfo.texas.TexasTokenXAuthPlugin")

val TexasTokenXAuthPlugin = createRouteScopedPlugin(
    name = "TexasTokenXAuthPlugin",
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
                client?.introspectToken("tokenx", bearerToken)
                    ?: error("TexasHttpClient is not configured")
            } catch (e: Exception) {
                call.application.environment.log.error("Failed to introspect token: ${e.message}", e)
                throw ApiErrorException.Unauthorized("Failed to introspect token", e)
            }

            if (!introspectionResponse.active) {
                call.application.environment.log.warn(
                    "" +
                            "Token is not active: ${introspectionResponse.error ?: "No error message"}"
                )
                throw ApiErrorException.Unauthorized("Token is not active")
            }
            if (!introspectionResponse.acr.equals("Level4", ignoreCase = true)) {
                call.application.environment.log.warn("User does not have Level4 access: ${introspectionResponse.acr}")
                throw ApiErrorException.Forbidden(
                    errorMessage = "User does not have Level4 access",
                    type = ErrorType.AUTHORIZATION_ERROR,
                )
            }

            if (introspectionResponse.pid == null) {
                call.application.environment.log.warn("No pid in token claims")
                throw ApiErrorException.Unauthorized("No pid in token claims")
            }
            call.authentication.principal(
                BrukerPrincipal(
                    ident = introspectionResponse.pid,
                    token = bearerToken,
                    clientId = introspectionResponse.clientId
                )
            )
        }
    }
    logger.info("TexasTokenXAuthPlugin installed")
}
