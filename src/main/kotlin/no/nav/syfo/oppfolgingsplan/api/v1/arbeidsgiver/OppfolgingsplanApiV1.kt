package no.nav.syfo.oppfolgingsplan.api.v1.arbeidsgiver

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.oppfolgingsplan.dto.CreateOppfolgingsplanRequest
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.dinesykmeldte.Sykmeldt
import no.nav.syfo.oppfolgingsplan.api.v1.extractAndValidateUUIDParameter
import no.nav.syfo.util.logger

fun Route.registerArbeidsgiverOppfolgingsplanApiV1(
    dineSykmeldteService: DineSykmeldteService,
    texasHttpClient: TexasHttpClient,
    oppfolgingsplanService: OppfolgingsplanService
) {
    val logger = logger()

    route("/arbeidsgiver/{narmesteLederId}/oppfolgingsplaner") {
        install(AuthorizeLeaderAccessToSykmeldtPlugin) {
            this.texasHttpClient = texasHttpClient
            this.dineSykmeldteService = dineSykmeldteService
        }
        fun checkIfOppfolgingsplanPropertiesBelongsToSykmelt(
            sykmeldtFnr: String,
            orgnummer: String,
            sykmeldt: Sykmeldt,
        ) {
            if (sykmeldtFnr != sykmeldt.fnr || orgnummer != sykmeldt.orgnummer) {
                logger.error("Sykmeldt fnr or orgnummer does not match for narmestelederId: ${sykmeldt.narmestelederId}")
                throw NotFoundException("Sykmeldt fnr or orgnummer does not match for narmestelederId: ${sykmeldt.narmestelederId}")
            }
        }

        post {
            val oppfolgingsplan = try {
                call.receive<CreateOppfolgingsplanRequest>()
            } catch (e: Exception) {
                call.application.environment.log.error("Failed to parse Oppfolgingsplan from request: ${e.message}", e)
                throw BadRequestException("Invalid Oppfolgingsplan format")
            }

            val sykmeldt = call.attributes[CALL_ATTRIBUTE_SYKMELDT]

            checkIfOppfolgingsplanPropertiesBelongsToSykmelt(
                oppfolgingsplan.sykmeldtFnr,
                oppfolgingsplan.orgnummer,
                sykmeldt
            )

            val uuid = oppfolgingsplanService.persistOppfolgingsplan(sykmeldt.narmestelederId, oppfolgingsplan)

            try {
                oppfolgingsplanService.produceVarsel(oppfolgingsplan)
            } catch (e: Exception) {
                logger.error("Error when producing kafka message", e)
            }
            call.response.headers.append(HttpHeaders.Location, call.request.path() + "/$uuid")
            call.respond(HttpStatusCode.Created)
        }

        /**
         * Gir et subsett av felter for alle oppfolgingsplaner arbeidsgiver har for sykmeldt identifisert via narmesteLederId.
         * Tiltenkt for oversiktsvisning.
         */
        get("/oversikt") {
            val sykmeldt = call.attributes[CALL_ATTRIBUTE_SYKMELDT]
            val oppfolgingsplaner =
                oppfolgingsplanService.getOppfolginsplanOverviewFor(sykmeldt.fnr, sykmeldt.orgnummer)

            call.respond(HttpStatusCode.OK, oppfolgingsplaner)
        }

        /**
         * Gir en komplett oppfolginsplan.
         */
        get("/{uuid}") {
            val sykmeldt = call.attributes[CALL_ATTRIBUTE_SYKMELDT]

            val uuid = call.parameters.extractAndValidateUUIDParameter()

            val oppfolgingsplan = oppfolgingsplanService.getOppfolgingsplanByUuid(uuid)
                ?: throw NotFoundException("Oppfolgingsplan not found for uuid: $uuid")

            checkIfOppfolgingsplanPropertiesBelongsToSykmelt(
                oppfolgingsplan.sykmeldtFnr,
                oppfolgingsplan.orgnummer,
                sykmeldt
            )

            call.respond(HttpStatusCode.OK, oppfolgingsplan)
        }
    }
}
