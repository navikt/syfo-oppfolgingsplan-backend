package no.nav.syfo.oppfolgingsplan.api.v1.arbeidsgiver

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import no.nav.syfo.application.auth.BrukerPrincipal
import no.nav.syfo.application.exception.ForbiddenException
import no.nav.syfo.application.exception.UnauthorizedException
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.oppfolgingsplan.db.domain.toResponse
import no.nav.syfo.oppfolgingsplan.dto.LagreUtkastRequest
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.texas.client.TexasHttpClient

fun Route.registerArbeidsgiverOppfolgingsplanUtkastApiV1(
    dineSykmeldteService: DineSykmeldteService,
    texasHttpClient: TexasHttpClient,
    oppfolgingsplanService: OppfolgingsplanService
) {
    route("/{narmesteLederId}/oppfolgingsplaner/utkast") {
        install(AuthorizeLeaderAccessToSykmeldtPlugin) {
            this.texasHttpClient = texasHttpClient
            this.dineSykmeldteService = dineSykmeldteService
        }

        put {
            val innloggetBruker = call.principal<BrukerPrincipal>()
                ?: throw UnauthorizedException("No user principal found in request")

            val utkast = try {
                call.receive<LagreUtkastRequest>()
            } catch (e: Exception) {
                throw BadRequestException("Invalid Oppfolgingsplan in request: ${e.message}", e)
            }

            val sykmeldt = call.attributes[CALL_ATTRIBUTE_SYKMELDT]

            if (sykmeldt.aktivSykmelding != true) {
                throw ForbiddenException("Sykmeldt does not have an active sykmelding")
            }

            val lagreUtkastResponse = oppfolgingsplanService.persistOppfolgingsplanUtkast(
                innloggetBruker.ident,
                sykmeldt,
                utkast
            )

            call.respond(HttpStatusCode.OK, lagreUtkastResponse)
        }

        delete {
            val sykmeldt = call.attributes[CALL_ATTRIBUTE_SYKMELDT]

            oppfolgingsplanService.deleteOppfolgingsplanUtkast(
                sykmeldt
            )

            call.respond(HttpStatusCode.NoContent)
        }

        get {
            val sykmeldt = call.attributes[CALL_ATTRIBUTE_SYKMELDT]

            val persistedOppfolgingsplanUtkast = oppfolgingsplanService.getPersistedOppfolgingsplanUtkast(sykmeldt)

            call.respond(
                HttpStatusCode.OK,
                persistedOppfolgingsplanUtkast.toResponse(sykmeldt)
            )
        }
    }
}
