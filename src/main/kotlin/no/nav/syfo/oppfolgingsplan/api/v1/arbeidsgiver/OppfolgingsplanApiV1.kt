package no.nav.syfo.oppfolgingsplan.api.v1.arbeidsgiver

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.syfo.application.auth.NarmesteLederPrincipal
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.oppfolgingsplan.dto.Oppfolgingsplan
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.texas.client.TexasHttpClient

fun Route.registerArbeidsgiverOppfolgingsplanApiV1(
    dineSykmeldteService: DineSykmeldteService,
    texasHttpClient: TexasHttpClient,
    oppfolgingsplanService: OppfolgingsplanService
) {

    route("/arbeidsgiver/{narmesteLederId}/oppfolgingsplaner") {
        install(ValidateAccessToSykmeldtPlugin) {
            this.texasHttpClient = texasHttpClient
            this.dineSykmeldteService = dineSykmeldteService
        }

        get {
            // TODO: Implement logic to retrieve oppfolgingsplan for the authenticated narmesteleder
            call.respond(HttpStatusCode.OK)
        }

        post {
            val oppfolgingsplan = try { call.receive<Oppfolgingsplan>() } catch (e: Exception) {
                call.application.environment.log.error("Failed to parse Oppfolgingsplan from request: ${e.message}", e)
                call.respond(HttpStatusCode.BadRequest, "Invalid Oppfolgingsplan format")
                return@post
            }
            val innloggetBruker = call.principal<NarmesteLederPrincipal>()
                ?: run {
                    call.application.environment.log.warn("No user principal found in request")
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }

            val sykmeldt = innloggetBruker.sykmeldt

            if (oppfolgingsplan.sykmeldtFnr != sykmeldt.fnr) {
                call.application.environment.log.warn("Sykmeldt fnr does not match for narmestelederId: ${sykmeldt.narmestelederId}")
                call.respond(HttpStatusCode.Forbidden, "Sykmeldt fnr does not match")
                return@post
            }
            oppfolgingsplanService.persistOppfolgingsplan(sykmeldt.narmestelederId, oppfolgingsplan)

            call.respond(HttpStatusCode.Created)
        }
    }
}