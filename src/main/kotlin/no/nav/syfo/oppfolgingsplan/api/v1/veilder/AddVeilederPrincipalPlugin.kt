package no.nav.syfo.oppfolgingsplan.api.v1.veilder

import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.principal
import io.ktor.util.AttributeKey
import no.nav.syfo.application.auth.VeilderPrincipal
import no.nav.syfo.application.exception.UnauthorizedException

class AuthorizeLeaderAccessToSykmeldtConfiguration()

const val NAV_PERSONIDENT_HEADER = "nav-personident"
val CALL_ATTRIBUTE_VEILEDER_PRINCIPAL = AttributeKey<VeilderPrincipal>("veilderPrincipal")

val AuthorizeVeilederAccessToSykmeldtPlugin = createRouteScopedPlugin(
    name = "AuthorizeLeaderAccessToSykmeldtPlugin",
    createConfiguration = ::AuthorizeLeaderAccessToSykmeldtConfiguration,
) {
    pluginConfig.apply {
        onCall { call ->
            val innloggetBruker = call.principal<VeilderPrincipal>()
                ?: throw UnauthorizedException("No user principal found in request")

            call.attributes[CALL_ATTRIBUTE_VEILEDER_PRINCIPAL] = innloggetBruker
        }
    }
}
