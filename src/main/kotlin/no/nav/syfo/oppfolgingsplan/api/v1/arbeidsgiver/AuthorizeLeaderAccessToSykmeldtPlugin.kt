package no.nav.syfo.oppfolgingsplan.api.v1.arbeidsgiver

import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.principal
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.util.AttributeKey
import no.nav.syfo.application.auth.BrukerPrincipal
import no.nav.syfo.application.exception.UnauthorizedException
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.texas.client.TexasHttpClient

class AuthorizeLeaderAccessToSykmeldtConfiguration(
    var texasHttpClient: TexasHttpClient? = null,
    var dineSykmeldteService: DineSykmeldteService? = null,
)

val CALL_ATTRIBUTE_SYKMELDT = AttributeKey<Sykmeldt>("sykmeldt")

/**
 * Checks that logged in user is the narmeste leder connected to the narmesteLederId parameter.
 * If so, adds the Sykmeldt connected to the narmesteLederId to the call attributes.
 */
val AuthorizeLeaderAccessToSykmeldtPlugin = createRouteScopedPlugin(
    name = "AuthorizeLeaderAccessToSykmeldtPlugin",
    createConfiguration = ::AuthorizeLeaderAccessToSykmeldtConfiguration,
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

            val texasResponse = texasHttpClient.exchangeTokenForDineSykmeldte(innloggetBruker.token)

            val sykmeldt = dineSykmeldteService.getSykmeldtForNarmesteleder(
                narmesteLederId,
                innloggetBruker.ident,
                texasResponse.accessToken
            )
                ?: throw NotFoundException("Sykmeldt not found for narmestelederId: $narmesteLederId")

            call.attributes[CALL_ATTRIBUTE_SYKMELDT] = sykmeldt
        }
    }
}
