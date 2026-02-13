package no.nav.syfo.oppfolgingsplan.api.v1.arbeidsgiver

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.syfo.application.auth.BrukerPrincipal
import no.nav.syfo.application.exception.ApiErrorException
import no.nav.syfo.application.exception.PlanNotFoundException
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.dokarkiv.DokarkivService
import no.nav.syfo.isdialogmelding.IsDialogmeldingService
import no.nav.syfo.oppfolgingsplan.api.v1.COUNT_OPPFOLGINGSPLAN_CREATED
import no.nav.syfo.oppfolgingsplan.api.v1.COUNT_OPPFOLGINGSPLAN_SHARED_WITH_GP
import no.nav.syfo.oppfolgingsplan.api.v1.COUNT_OPPFOLGINGSPLAN_SHARED_WITH_NAV
import no.nav.syfo.oppfolgingsplan.api.v1.extractAndValidateUUIDParameter
import no.nav.syfo.oppfolgingsplan.db.domain.toResponse
import no.nav.syfo.oppfolgingsplan.dto.CreateOppfolgingsplanRequest
import no.nav.syfo.oppfolgingsplan.dto.DelMedLegeResponse
import no.nav.syfo.oppfolgingsplan.dto.DelMedVeilederResponse
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.pdfgen.PdfGenService
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.util.logger
import java.time.Instant

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

    route("/{narmesteLederId}/oppfolgingsplaner") {
        install(AuthorizeLeaderAccessToSykmeldtPlugin) {
            this.texasHttpClient = texasHttpClient
            this.dineSykmeldteService = dineSykmeldteService
        }
        fun checkIfOppfolgingsplanPropertiesBelongsToSykmeldt(
            sykmeldtFnr: String,
            orgnummer: String,
            sykmeldt: Sykmeldt,
        ) {
            if (sykmeldtFnr != sykmeldt.fnr || orgnummer != sykmeldt.orgnummer) {
                logger.error(
                    "Sykmeldt fnr or orgnummer does not match for narmestelederId: ${sykmeldt.narmestelederId}"
                )
                throw ApiErrorException.NotFound(
                    "Sykmeldt fnr or orgnummer does not match for narmestelederId: ${sykmeldt.narmestelederId}"
                )
            }
        }

        post {
            val innloggetBruker = call.principal<BrukerPrincipal>()
                ?: throw ApiErrorException.Unauthorized("No user principal found in request")

            val oppfolgingsplan = try {
                call.receive<CreateOppfolgingsplanRequest>()
            } catch (e: Exception) {
                throw ApiErrorException.BadRequest("Invalid Oppfolgingsplan in request: ${e.message}", e)
            }

            val sykmeldt = call.attributes[CALL_ATTRIBUTE_SYKMELDT]

            if (sykmeldt.aktivSykmelding != true) {
                throw ApiErrorException.Forbidden(
                    "Cannot create oppfolgingsplan for sykmeldt without active sykmelding",
                )
            }

            val uuid = oppfolgingsplanService.createOppfolgingsplan(
                innloggetBruker.ident,
                sykmeldt,
                oppfolgingsplan
            )

            COUNT_OPPFOLGINGSPLAN_CREATED.increment()
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
                oppfolgingsplanService.getOppfolgingsplanOverviewFor(sykmeldt)

            call.respond(HttpStatusCode.OK, oppfolgingsplaner)
        }

        /**
         * Gir en komplett aktiv oppfolgingsplan.
         */
        get("/aktiv-plan") {
            val sykmeldt = call.attributes[CALL_ATTRIBUTE_SYKMELDT]

            val aktivPlan =
                oppfolgingsplanService.getAktivplanForSykmeldt(sykmeldt)
                    ?: throw PlanNotFoundException("Aktiv plan not found")

            checkIfOppfolgingsplanPropertiesBelongsToSykmeldt(
                aktivPlan.sykmeldtFnr,
                aktivPlan.organisasjonsnummer,
                sykmeldt
            )

            call.respond(HttpStatusCode.OK, aktivPlan.toResponse(sykmeldt.aktivSykmelding == true))
        }

        /**
         * Gir en komplett oppfolgingsplan.
         */
        get("/{uuid}") {
            val sykmeldt = call.attributes[CALL_ATTRIBUTE_SYKMELDT]

            val uuid = call.parameters.extractAndValidateUUIDParameter()

            val persistedOppfolgingsplan = oppfolgingsplanService.getPersistedOppfolgingsplanByUuid(uuid)

            checkIfOppfolgingsplanPropertiesBelongsToSykmeldt(
                persistedOppfolgingsplan.sykmeldtFnr,
                persistedOppfolgingsplan.organisasjonsnummer,
                sykmeldt
            )

            call.respond(
                HttpStatusCode.OK,
                persistedOppfolgingsplan.toResponse(sykmeldt.aktivSykmelding == true)
            )
        }

        post("/{uuid}/del-med-lege") {
            val sykmeldt = call.attributes[CALL_ATTRIBUTE_SYKMELDT]

            if (sykmeldt.aktivSykmelding != true) {
                throw ApiErrorException.BadRequest(
                    "Cannot send oppfolgingsplan to general practitioner when there is no active sykmelding"
                )
            }

            val innloggetBruker = call.principal<BrukerPrincipal>()
                ?: throw ApiErrorException.Unauthorized("No user principal found in request")

            val uuid = call.parameters.extractAndValidateUUIDParameter()

            val oppfolgingsplan = oppfolgingsplanService.getPersistedOppfolgingsplanByUuid(uuid)

            checkIfOppfolgingsplanPropertiesBelongsToSykmeldt(
                oppfolgingsplan.sykmeldtFnr,
                oppfolgingsplan.organisasjonsnummer,
                sykmeldt
            )

            if (oppfolgingsplan.deltMedLegeTidspunkt != null) {
                throw ApiErrorException.Conflict("Oppfolgingsplan is already shared with general practitioner")
            }
            oppfolgingsplanService.updateSkalDelesMedLege(uuid, true)

            val pdfByteArray = pdfGenService.generatePdf(oppfolgingsplan)
                ?: throw ApiErrorException.InternalServerError("An error occurred while generating pdf")

            val texasResponse = texasHttpClient.exchangeTokenForIsDialogmelding(innloggetBruker.token)
            isDialogmeldingService.sendOppfolgingsplanToGeneralPractitioner(
                texasResponse.accessToken,
                sykmeldt.fnr,
                pdfByteArray
            )

            val deltMedLegeTidspunkt = Instant.now()

            oppfolgingsplanService.setDeltMedLegeTidspunkt(uuid, deltMedLegeTidspunkt)
            COUNT_OPPFOLGINGSPLAN_SHARED_WITH_GP.increment()

            call.respond(HttpStatusCode.OK, DelMedLegeResponse(deltMedLegeTidspunkt))
        }

        post("/{uuid}/del-med-veileder") {
            val sykmeldt = call.attributes[CALL_ATTRIBUTE_SYKMELDT]

            if (sykmeldt.aktivSykmelding != true) {
                throw ApiErrorException.BadRequest(
                    "Cannot send oppfolgingsplan to Nav when there is no active sykmelding"
                )
            }

            val uuid = call.parameters.extractAndValidateUUIDParameter()

            val oppfolgingsplan = oppfolgingsplanService.getPersistedOppfolgingsplanByUuid(uuid)

            checkIfOppfolgingsplanPropertiesBelongsToSykmeldt(
                oppfolgingsplan.sykmeldtFnr,
                oppfolgingsplan.organisasjonsnummer,
                sykmeldt
            )

            if (oppfolgingsplan.deltMedVeilederTidspunkt != null) {
                throw ApiErrorException.Conflict("Oppfolgingsplan is already shared with Veileder")
            }

            val pdfByteArray = pdfGenService.generatePdf(oppfolgingsplan)
                ?: throw ApiErrorException.InternalServerError("An error occurred while generating pdf")
            try {
                val journalpostId = dokarkivService.arkiverOppfolgingsplan(oppfolgingsplan, pdfByteArray)
                val deltMedVeilederTidspunkt = oppfolgingsplanService.updateDelingAvPlanMedVeileder(
                    uuid, journalpostId
                )

                COUNT_OPPFOLGINGSPLAN_SHARED_WITH_NAV.increment()

                call.respond(HttpStatusCode.OK, DelMedVeilederResponse(deltMedVeilederTidspunkt))
            } catch (e: Exception) {
                logger.error("Failed to archive oppfolgingsplan with uuid: $uuid", e)
                throw ApiErrorException.InternalServerError("An error occurred while archiving oppfolgingsplan")
            }
        }

        get("/{uuid}/pdf") {
            val sykmeldt = call.attributes[CALL_ATTRIBUTE_SYKMELDT]
            val uuid = call.parameters.extractAndValidateUUIDParameter()

            val persistedOppfolgingsplan = oppfolgingsplanService.getPersistedOppfolgingsplanByUuid(uuid)

            checkIfOppfolgingsplanPropertiesBelongsToSykmeldt(
                persistedOppfolgingsplan.sykmeldtFnr,
                persistedOppfolgingsplan.organisasjonsnummer,
                sykmeldt
            )

            val pdfByteArray = pdfGenService.generatePdf(persistedOppfolgingsplan)
                ?: throw ApiErrorException.InternalServerError("An error occurred while generating pdf")

            call.response.status(HttpStatusCode.OK)
            call.response.headers.append(HttpHeaders.ContentType, "application/pdf")
            call.respond<ByteArray>(pdfByteArray)
        }
    }
}
