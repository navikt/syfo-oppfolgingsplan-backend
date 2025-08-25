package no.nav.syfo.oppfolgingsplan.api.v1.arbeidsgiver

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import no.nav.syfo.application.auth.BrukerPrincipal
import no.nav.syfo.application.exception.UnauthorizedException
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.oppfolgingsplan.dto.CreateUtkastRequest
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.util.logger

fun Route.registerArbeidsgiverOppfolgingsplanUtkastApiV1(
    dineSykmeldteService: DineSykmeldteService,
    texasHttpClient: TexasHttpClient,
    oppfolgingsplanService: OppfolgingsplanService
) {
    val logger = logger()

    route("/arbeidsgiver/{narmesteLederId}/oppfolgingsplaner/utkast") {
        install(AuthorizeLeaderAccessToSykmeldtPlugin) {
            this.texasHttpClient = texasHttpClient
            this.dineSykmeldteService = dineSykmeldteService
        }

        put {
            val innloggetBruker = call.principal<BrukerPrincipal>()
                ?: throw UnauthorizedException("No user principal found in request")

            val utkast = try {
                val plan = call.receive<CreateUtkastRequest>()
                plan.content?.validateFields()
                plan
            } catch (e: Exception) {
                logger.warn("Failed to parse Oppfolgingsplan from request", e)
                throw BadRequestException("Invalid Oppfolgingsplan in request: ${e.message}", e)
            }

            val sykmeldt = call.attributes[CALL_ATTRIBUTE_SYKMELDT]

            oppfolgingsplanService.persistOppfolgingsplanUtkast(
                innloggetBruker.ident,
                sykmeldt,
                utkast
            )

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
