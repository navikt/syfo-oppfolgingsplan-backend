package no.nav.syfo.application.auth

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.principal
import io.ktor.server.request.uri
import no.nav.syfo.application.exception.ForbiddenException
import no.nav.syfo.application.exception.UnauthorizedException
import no.nav.syfo.util.logger

private val logger = logger("no.nav.syfo.application.auth.ClientAuthorizationPlugin")

class ClientAuthorizationPluginConfig {
    lateinit var allowedClientId: String
}

val ClientAuthorizationPlugin = createRouteScopedPlugin(
    name = "ClientAuthorizationPlugin",
    createConfiguration = ::ClientAuthorizationPluginConfig,
) {
    val clientId = pluginConfig.allowedClientId

    onCall { call ->
        call.requireClient(clientId)
    }
}

private fun ApplicationCall.requireClient(allowedClientId: String) {
    val principal = principal<BrukerPrincipal>()
        ?: throw UnauthorizedException("No user principal found in request")
    val callerClientId = principal.azp
        ?: throw UnauthorizedException("Missing azp claim in token")
    if (callerClientId != allowedClientId) {
        logger.error(
            "Client authorization failed - expected: $allowedClientId, actual: $callerClientId, path: ${request.uri}"
        )
        throw ForbiddenException("Caller is not authorized for this endpoint")
    }
}


