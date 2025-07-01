package no.nav.syfo.oppfolgingsplan.api.v1.arbeidsgiver

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.oppfolgingsplan.dto.OppfolgingsplanUtkast
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.texas.client.TexasHttpClient

fun Route.registerArbeidsgiverOppfolgingsplanUtkastApiV1(
    dineSykmeldteService: DineSykmeldteService,
    texasHttpClient: TexasHttpClient,
    oppfolgingsplanService: OppfolgingsplanService
) {

    route("/arbeidsgiver/{narmesteLederId}/oppfolgingsplaner/utkast") {
        install(ValidateAccessToSykmeldtPlugin) {
            this.texasHttpClient = texasHttpClient
            this.dineSykmeldteService = dineSykmeldteService
        }

        put {
            val utkast = try { call.receive<OppfolgingsplanUtkast>() } catch (e: Exception) {
                call.application.environment.log.error("Failed to parse OppfolgingsplanUtkast from request: ${e.message}", e)
                call.respond(HttpStatusCode.BadRequest, "Invalid OppfolgingsplanUtkast format")
                return@put
            }

            val sykmeldt = call.attributes[CALL_ATTRIBUTE_SYKMELDT]

            if (utkast.sykmeldtFnr != sykmeldt.fnr) {
                call.application.environment.log.warn("Sykmeldt fnr does not match for narmestelederId: ${sykmeldt.narmestelederId}")
                call.respond(HttpStatusCode.Forbidden, "Sykmeldt fnr does not match")
                return@put
            }

            oppfolgingsplanService.persistOppfolgingsplanUtkast(sykmeldt.narmestelederId, utkast)

            call.respond(HttpStatusCode.OK)
        }

        get {
            val sykmeldt = call.attributes[CALL_ATTRIBUTE_SYKMELDT]

            val utkast = oppfolgingsplanService.getOppfolgingsplanUtkast(sykmeldt.narmestelederId)

            if (utkast == null) {
                call.respond(HttpStatusCode.NotFound, "No draft found for the given narmestelederId")
            } else {
                call.respond(HttpStatusCode.OK, utkast)
            }
        }
    }
}
