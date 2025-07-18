package no.nav.syfo.oppfolgingsplan.api.v1.arbeidsgiver

import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.principal
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.util.AttributeKey
import no.nav.syfo.application.auth.BrukerPrincipal
import no.nav.syfo.application.exception.UnauthorizedException
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.dinesykmeldte.Sykmeldt
import no.nav.syfo.texas.client.TexasHttpClient

class ValidateAccessToSykmeldtConfiguration(
    var texasHttpClient: TexasHttpClient? = null,
    var dineSykmeldteService: DineSykmeldteService? = null,
)

val CALL_ATTRIBUTE_SYKMELDT = AttributeKey<Sykmeldt>("sykmeldt")

val ValidateAccessToSykmeldtPlugin = createRouteScopedPlugin(
    name = "ValidateAccessToSykmeldtPlugin",
    createConfiguration = ::ValidateAccessToSykmeldtConfiguration,
) {
    pluginConfig.apply {
        onCall { call ->
            val texasHttpClient = texasHttpClient
                ?: throw throw IllegalStateException("TexasHttpClient must be provided in ValidateAccessToSykmeldtPlugin configuration")

            val dineSykmeldteService = dineSykmeldteService
                ?: throw IllegalStateException("DineSykmeldteService must be provided in ValidateAccessToSykmeldtPlugin configuration")

            val narmesteLederId = call.parameters["narmesteLederId"]
                ?: throw BadRequestException("Missing narmesteLederId parameter in request")

            val innloggetBruker = call.principal<BrukerPrincipal>()
                ?: throw UnauthorizedException("No user principal found in request")

            val texasResponse = texasHttpClient.exhangeTokenForDineSykmeldte(innloggetBruker.token)

            val sykmeldt = dineSykmeldteService.getSykmeldtForNarmesteleder(narmesteLederId, texasResponse.accessToken)
                ?: throw NotFoundException("Sykmeldt not found for narmestelederId: $narmesteLederId")

            call.attributes[CALL_ATTRIBUTE_SYKMELDT] = sykmeldt
        }
    }
}
