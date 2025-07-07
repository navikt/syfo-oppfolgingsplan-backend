package no.nav.syfo.oppfolgingsplan.api.v1.sykemeldt

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import java.util.UUID
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.util.logger

fun Route.registerSykemeldtOppfolgingsplanApiV1(
    texasHttpClient: TexasHttpClient,
    oppfolgingsplanService: OppfolgingsplanService
) {
    route("/sykmeldt/oppfolgingsplaner") {
        install(ValidateBrukerPrincipalPlugin) {
            this.texasHttpClient = texasHttpClient
        }
        get("/oversikt") {
            val principal = call.attributes[CALL_ATTRIBUTE_BRUKER_PRINCIPAL]
            val oppfolgingsplaner =
                oppfolgingsplanService.getOppfolginsplanOverviewFor(principal.ident)

            call.respond(HttpStatusCode.OK, oppfolgingsplaner)
        }

        get("/{uuid}") {
            val principal = call.attributes[CALL_ATTRIBUTE_BRUKER_PRINCIPAL]
            val uuid = call.parameters["uuid"]
                ?: run {
                    call.application.environment.log.warn("No uuid found in request parameters")
                    call.respond(HttpStatusCode.BadRequest, "Missing uuid parameter")
                    return@get
                }
            val oppfolgingsplan = oppfolgingsplanService.getOppfolgingsplanByUuid(UUID.fromString(uuid))
                ?: run {
                    call.application.environment.log.warn("Oppfolgingsplan not found for uuid: $uuid")
                    call.respond(HttpStatusCode.NotFound, "Oppfolgingsplan not found")
                    return@get
                }

            if (oppfolgingsplan.sykmeldtFnr != principal.ident) {
                val message = "Oppfolginsplan with uuid: ${uuid} does not belong to logged in user"
                call.application.environment.log.warn(message)
                call.respond(HttpStatusCode.Forbidden, message)
                return@get
            }

            call.respond(HttpStatusCode.OK, oppfolgingsplan)
        }
    }
}
