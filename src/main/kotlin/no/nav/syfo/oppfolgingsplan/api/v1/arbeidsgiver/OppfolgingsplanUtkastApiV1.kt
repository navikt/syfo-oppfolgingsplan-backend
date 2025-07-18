package no.nav.syfo.oppfolgingsplan.api.v1.arbeidsgiver

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import no.nav.syfo.application.exception.ApiError
import no.nav.syfo.application.exception.ForbiddenException
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.oppfolgingsplan.db.PersistedOppfolgingsplanUtkast
import no.nav.syfo.oppfolgingsplan.dto.CreateUtkastRequest
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.util.logger

fun Route.registerArbeidsgiverOppfolgingsplanUtkastApiV1(
    dineSykmeldteService: DineSykmeldteService,
    texasHttpClient: TexasHttpClient,
    oppfolgingsplanService: OppfolgingsplanService
) {
    route("/arbeidsgiver/{narmesteLederId}/oppfolgingsplaner/utkast") {
        install(AuthorizeLeaderAccessToSykmeldtPlugin) {
            this.texasHttpClient = texasHttpClient
            this.dineSykmeldteService = dineSykmeldteService
        }

        put {
            val utkast = try { call.receive<CreateUtkastRequest>() } catch (e: Exception) {
                throw BadRequestException("Failed to parse OppfolgingsplanUtkast from request", e)
            }

            val sykmeldt = call.attributes[CALL_ATTRIBUTE_SYKMELDT]

            if (utkast.sykmeldtFnr != sykmeldt.fnr) {
                throw ForbiddenException("Sykmeldt fnr does not match for narmestelederId: ${sykmeldt.narmestelederId}")
            }
            oppfolgingsplanService.persistOppfolgingsplanUtkast(sykmeldt.narmestelederId, utkast)
            call.respond(HttpStatusCode.OK)
        }

        get {
            val sykmeldt = call.attributes[CALL_ATTRIBUTE_SYKMELDT]

            oppfolgingsplanService.getOppfolgingsplanUtkast(sykmeldt.fnr, sykmeldt.orgnummer)?.let { utkast ->
                call.respond(HttpStatusCode.OK, utkast)
            } ?: throw NotFoundException("No draft found for the given narmestelederId")
        }
    }
}
