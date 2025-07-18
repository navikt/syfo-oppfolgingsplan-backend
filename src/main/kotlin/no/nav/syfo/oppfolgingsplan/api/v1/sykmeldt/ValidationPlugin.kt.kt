package no.nav.syfo.oppfolgingsplan.api.v1.sykmeldt

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey
import no.nav.syfo.application.auth.BrukerPrincipal
import no.nav.syfo.application.exception.UnauthorizedException
import no.nav.syfo.texas.client.TexasHttpClient

class ValidateBrukerPrincipalConfiguration(
    var texasHttpClient: TexasHttpClient? = null,
)

val CALL_ATTRIBUTE_BRUKER_PRINCIPAL = AttributeKey<BrukerPrincipal>("brukerPrincipal")

val ValidateBrukerPrincipalPlugin = createRouteScopedPlugin(
    name = "ValidateSykmeldtPlugin",
    createConfiguration = ::ValidateBrukerPrincipalConfiguration,
) {
    pluginConfig.apply {
        onCall { call ->
            val innloggetBruker = call.principal<BrukerPrincipal>()
                ?: throw UnauthorizedException("No user principal found in request")

            call.attributes[CALL_ATTRIBUTE_BRUKER_PRINCIPAL] = innloggetBruker
        }
    }
}
