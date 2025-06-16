package no.nav.syfo.oppfolgingsplan

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.syfo.application.auth.BrukerPrincipal
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.oppfolgingsplan.domain.Oppfolgingsplan
import no.nav.syfo.texas.TexasAuthPlugin
import no.nav.syfo.texas.client.TexasHttpClient

fun Routing.registerOppfolgingsplanApi(
    texasHttpClient: TexasHttpClient,
    dineSykmeldteService: DineSykmeldteService
) {
    route("api/v1/narmesteleder/{narmesteLederId}/oppfolgingsplaner") {
        install(TexasAuthPlugin) {
            client = texasHttpClient
        }

        get {
            // TODO: Implement logic to retrieve oppfolgingsplan for the authenticated narmesteleder
            call.respond(HttpStatusCode.OK)
        }

        post {
            val narmesteLederId = call.parameters["narmesteLederId"]
                ?: run {
                    call.application.environment.log.warn("No narmesteLederId found in request parameters")
                    call.respond(HttpStatusCode.BadRequest, "Missing narmesteLederId parameter")
                    return@post
                }

            val bruker = call.principal<BrukerPrincipal>()
                ?: run {
                    call.application.environment.log.warn("No user principal found in request")
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }

            val texasResponse = texasHttpClient.exhangeTokenForDineSykmeldte(bruker.token)

            val sykmeldt = dineSykmeldteService.getSykmeldtForNarmesteleder(narmesteLederId, texasResponse.accessToken)
            if (sykmeldt == null) {
                call.application.environment.log.warn("Sykmeldt not found for narmestelederId: $narmesteLederId")
                call.respond(HttpStatusCode.NotFound)
                return@post
            }
            if (sykmeldt.aktivSykmelding == null || !sykmeldt.aktivSykmelding) {
                call.application.environment.log.warn("Sykmeldt is not aktiv sykmeldt. NarmestelederId: $narmesteLederId")
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }
            // TODO: Implement logic to store the oppfolgingsplan
            val oppfolgingsplan = call.receive<Oppfolgingsplan>()
            call.respond(HttpStatusCode.Created)
        }
    }
}