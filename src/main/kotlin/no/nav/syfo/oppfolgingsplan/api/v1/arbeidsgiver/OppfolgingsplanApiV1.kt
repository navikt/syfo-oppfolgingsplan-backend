package no.nav.syfo.oppfolgingsplan.api.v1.arbeidsgiver

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.syfo.application.auth.BrukerPrincipal
import no.nav.syfo.application.exception.InternalServerErrorException
import no.nav.syfo.application.exception.UnauthorizedException
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.oppfolgingsplan.dto.CreateOppfolgingsplanRequest
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.dinesykmeldte.Sykmeldt
import no.nav.syfo.isdialogmelding.IsDialogmeldingService
import no.nav.syfo.oppfolgingsplan.api.v1.extractAndValidateUUIDParameter
import no.nav.syfo.pdfgen.PdfGenService
import no.nav.syfo.util.logger
import java.time.Instant

fun Route.registerArbeidsgiverOppfolgingsplanApiV1(
    dineSykmeldteService: DineSykmeldteService,
    texasHttpClient: TexasHttpClient,
    oppfolgingsplanService: OppfolgingsplanService,
    pdfGenService: PdfGenService,
    isDialogmeldingService: IsDialogmeldingService
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
            val innloggetBruker = call.principal<BrukerPrincipal>()
                ?: throw UnauthorizedException("No user principal found in request")

            val oppfolgingsplan = try {
                call.receive<CreateOppfolgingsplanRequest>()
            } catch (e: Exception) {
                logger.warn("Failed to parse Oppfolgingsplan from request: ${e.message}", e)
                throw BadRequestException("Invalid Oppfolgingsplan format")
            }

            val sykmeldt = call.attributes[CALL_ATTRIBUTE_SYKMELDT]

            val uuid = oppfolgingsplanService.createOppfolgingsplan(
                innloggetBruker.ident,
                sykmeldt,
                oppfolgingsplan
            )

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
                oppfolgingsplan.organisasjonsnummer,
                sykmeldt
            )

            call.respond(HttpStatusCode.OK, oppfolgingsplan)
        }

        post("/{uuid}/del-med-lege") {
            val sykmeldt = call.attributes[CALL_ATTRIBUTE_SYKMELDT]

            if (sykmeldt.aktivSykmelding != true) {
                throw BadRequestException("Cannot send oppfolgingsplan to general practitioner when there is no active sykmelding")
            }

            val innloggetBruker = call.principal<BrukerPrincipal>()
                ?: throw UnauthorizedException("No user principal found in request")

            val uuid = call.parameters.extractAndValidateUUIDParameter()

            val oppfolgingsplan = oppfolgingsplanService.getOppfolgingsplanByUuid(uuid)
                ?: throw NotFoundException("Oppfolgingsplan not found for uuid: $uuid")

            checkIfOppfolgingsplanPropertiesBelongsToSykmelt(
                oppfolgingsplan.sykmeldtFnr,
                oppfolgingsplan.organisasjonsnummer,
                sykmeldt
            )

            oppfolgingsplanService.updateSkalDelesMedLege(uuid, true)

            val pdfByteArray = pdfGenService.generatePdf(oppfolgingsplan)
                ?: throw InternalServerErrorException("An error occurred while generating pdf")

            val texasResponse = texasHttpClient.exchangeTokenForIsDialogmelding(innloggetBruker.token)
            isDialogmeldingService.sendOppfolgingsplanToGeneralPractitioner(
                texasResponse.accessToken,
                sykmeldt.fnr,
                pdfByteArray
            )

            oppfolgingsplanService.setDeltMedLegeTidspunkt(uuid, Instant.now())
            call.respond(HttpStatusCode.OK)
        }

        post("/{uuid}/del-med-Nav") {
            val sykmeldt = call.attributes[CALL_ATTRIBUTE_SYKMELDT]

            if (sykmeldt.aktivSykmelding != true) {
                throw BadRequestException("Cannot send oppfolgingsplan to Nav when there is no active sykmelding")
            }

            val innloggetBruker = call.principal<BrukerPrincipal>()
                ?: throw UnauthorizedException("No user principal found in request")

            val uuid = call.parameters.extractAndValidateUUIDParameter()

            val oppfolgingsplan = oppfolgingsplanService.getOppfolgingsplanByUuid(uuid)
                ?: throw NotFoundException("Oppfolgingsplan not found for uuid: $uuid")

            checkIfOppfolgingsplanPropertiesBelongsToSykmelt(
                oppfolgingsplan.sykmeldtFnr,
                oppfolgingsplan.orgnummer,
                sykmeldt
            )

            oppfolgingsplanService.updateSkalDelesMedLege(uuid, true)

            val pdfByteArray = pdfGenService.generatePdf(oppfolgingsplan)
                ?: throw InternalServerErrorException("An error occurred while generating pdf")

            val texasResponse = texasHttpClient.exchangeTokenForIsDialogmelding(innloggetBruker.token)
            isDialogmeldingService.sendOppfolgingsplanToGeneralPractitioner(
                texasResponse.accessToken,
                sykmeldt.fnr,
                pdfByteArray)

            oppfolgingsplanService.setDeltMedLegeTidspunkt(uuid, Instant.now())
            call.respond(HttpStatusCode.OK)
        }
    }
}
