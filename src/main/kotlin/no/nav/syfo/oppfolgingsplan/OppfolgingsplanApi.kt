package no.nav.syfo.oppfolgingsplan

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.oppfolgingsplan.domain.Oppfolgingsplan
import no.nav.syfo.texas.TexasAuthPlugin
import no.nav.syfo.texas.TexasHttpClient
import no.nav.syfo.texas.bearerToken

fun Routing.registerOppfolgingsplanApi(
    texasHttpClient: TexasHttpClient,
    dineSykmeldteService: DineSykmeldteService
) {
    route("api/v1/oppfolgingsplaner") {
        install(TexasAuthPlugin) {
            client = texasHttpClient
        }

        get {
            // TODO: Implement logic to retrieve oppfolgingsplan for the authenticated narmesteleder
            call.respond(HttpStatusCode.OK)
        }

        post {
            val oppfolgingsplan = call.receive<Oppfolgingsplan>()
            val texasResponse = texasHttpClient.exchangeToken("dev-gcp:team-esyfo:dinesykmeldte-backend", call.bearerToken()!!)
            val sykmeldt = dineSykmeldteService.getSykmeldtForNarmesteleder(oppfolgingsplan.narmestelederId, texasResponse.accessToken)
            if (sykmeldt == null) {
                call.application.environment.log.warn("Sykmeldt not found for narmestelederId: ${oppfolgingsplan.narmestelederId}")
                call.respond(HttpStatusCode.NotFound)
                return@post
            }
            if (sykmeldt.aktivSykmelding == null || !sykmeldt.aktivSykmelding) {
                call.application.environment.log.warn("Sykmeldt is not aktiv sykmeldt. NarmestelederId: ${oppfolgingsplan.narmestelederId}")
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }
            // TODO: Implement logic to store the oppfolgingsplan
            call.respond(HttpStatusCode.Created)
        }
    }
}