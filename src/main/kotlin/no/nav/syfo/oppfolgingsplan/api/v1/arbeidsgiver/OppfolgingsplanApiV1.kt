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
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.isdialogmelding.IsDialogmeldingService
import no.nav.syfo.oppfolgingsplan.api.v1.extractAndValidateUUIDParameter
import no.nav.syfo.pdfgen.PdfGenService
import no.nav.syfo.util.logger
import java.time.Instant
import no.nav.syfo.application.exception.ConflictException
import no.nav.syfo.dokarkiv.DokarkivService
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.validateFields

@Suppress("LongParameterList", "LongMethod", "ThrowsCount")
fun Route.registerArbeidsgiverOppfolgingsplanApiV1(
    dineSykmeldteService: DineSykmeldteService,
    dokarkivService: DokarkivService,
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
                logger.error(
                    "Sykmeldt fnr or orgnummer does not match for narmestelederId: ${sykmeldt.narmestelederId}"
                )
                throw NotFoundException(
                    "Sykmeldt fnr or orgnummer does not match for narmestelederId: ${sykmeldt.narmestelederId}"
                )
            }
        }

        post {
            val innloggetBruker = call.principal<BrukerPrincipal>()
                ?: throw UnauthorizedException("No user principal found in request")

            val oppfolgingsplan = try {
                val plan = call.receive<CreateOppfolgingsplanRequest>()
                plan.content.validateFields()
                plan
            } catch (e: Exception) {
                throw BadRequestException("Invalid Oppfolgingsplan in request: ${e.message}", e)
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
         * Gir et subsett av felter for alle oppfolgingsplaner arbeidsgiver har for sykmeldt
         * identifisert via narmesteLederId.
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
                throw BadRequestException(
                    "Cannot send oppfolgingsplan to general practitioner when there is no active sykmelding"
                )
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

            if (oppfolgingsplan.deltMedLegeTidspunkt != null) {
                throw ConflictException("Oppfolgingsplan is already shared with general practitioner")
            }
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

        post("/{uuid}/del-med-veileder") {
            val sykmeldt = call.attributes[CALL_ATTRIBUTE_SYKMELDT]

            if (sykmeldt.aktivSykmelding != true) {
                throw BadRequestException("Cannot send oppfolgingsplan to Nav when there is no active sykmelding")
            }

            val uuid = call.parameters.extractAndValidateUUIDParameter()

            val oppfolgingsplan = oppfolgingsplanService.getOppfolgingsplanByUuid(uuid)
                ?: throw NotFoundException("Oppfolgingsplan not found for uuid: $uuid")

            checkIfOppfolgingsplanPropertiesBelongsToSykmelt(
                oppfolgingsplan.sykmeldtFnr,
                oppfolgingsplan.organisasjonsnummer,
                sykmeldt
            )

            if (oppfolgingsplan.deltMedVeilederTidspunkt != null) {
                throw ConflictException("Oppfolgingsplan is already shared with Veileder")
            }

            oppfolgingsplanService.updateSkalDelesMedVeileder(uuid, true)
            val pdfByteArray = pdfGenService.generatePdf(oppfolgingsplan)
                ?: throw InternalServerErrorException("An error occurred while generating pdf")
            try {
                dokarkivService.arkiverOppfolginsplan(oppfolgingsplan, pdfByteArray)
                oppfolgingsplanService.setDeltMedVeilederTidspunkt(uuid, Instant.now())
                call.respond(HttpStatusCode.OK)
            } catch (e: Exception) {
                logger.error("Failed to archive oppfolgingsplan with uuid: $uuid", e)
                throw InternalServerErrorException("An error occurred while archiving oppfolgingsplan")
            }
        }
    }
}
