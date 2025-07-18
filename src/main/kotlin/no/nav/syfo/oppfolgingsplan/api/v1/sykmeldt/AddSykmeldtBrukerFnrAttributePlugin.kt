package no.nav.syfo.oppfolgingsplan.api.v1.sykmeldt

import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.principal
import io.ktor.util.AttributeKey
import no.nav.syfo.application.auth.BrukerPrincipal
import no.nav.syfo.oppfolgingsplan.domain.Fodselsnummer
import no.nav.syfo.application.exception.UnauthorizedException
import no.nav.syfo.texas.client.TexasHttpClient

class AddSykmeldtBrukerFnrAttributePluginConfiguration(
    var texasHttpClient: TexasHttpClient? = null,
)

val CALL_ATTRIBUTE_SYKMELDT_BRUKER_FODSELSNUMMER = AttributeKey<Fodselsnummer>("sykmeldtBrukerFodselsnummer")

val AddSykmeldtBrukerFnrAttributePlugin = createRouteScopedPlugin(
    name = "AddSykmeldtBrukerFnrAttributePlugin",
    createConfiguration = ::AddSykmeldtBrukerFnrAttributePluginConfiguration,
) {
    pluginConfig.apply {
        onCall { call ->
            val innloggetBruker = call.principal<BrukerPrincipal>()
                ?: throw UnauthorizedException("No user principal found in request")

            call.attributes[CALL_ATTRIBUTE_SYKMELDT_BRUKER_FODSELSNUMMER] = Fodselsnummer(innloggetBruker.ident)
        }
    }
}
