package no.nav.syfo.application.auth

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.principal
import io.ktor.server.request.uri
import no.nav.syfo.application.exception.ApiErrorException
import no.nav.syfo.application.isProdEnv
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

    val allowedClients = if (isProdEnv()) {
        listOf(clientId)
    } else {
        listOf(clientId, "dev-gcp:nais:tokenx-token-generator", "dev-gcp:nais:azure-token-generator")
    }

    onCall { call ->
        call.requireClient(allowedClients)
    }
}

private fun ApplicationCall.requireClient(allowedClients: List<String>) {
    val principal = principal<BrukerPrincipal>()
        ?: throw ApiErrorException.Unauthorized("No user principal found in request")
    val callerClientId = principal.clientId
        ?: throw ApiErrorException.Unauthorized("Missing azp claim in token")
    if (!allowedClients.contains(callerClientId)) {
        logger.error(
            "Client authorization failed - expected: $allowedClients, actual: $callerClientId, path: ${request.uri}"
        )
        throw ApiErrorException.Forbidden("Caller is not authorized for this endpoint")
    }
}
