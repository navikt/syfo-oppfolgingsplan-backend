package no.nav.syfo.oppfolgingsplan.api.v1.sykmeldt

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.syfo.application.exception.ApiErrorException
import no.nav.syfo.foresporsel.ForesporselService
import no.nav.syfo.foresporsel.dto.BeOmPlanRequest
import no.nav.syfo.oppfolgingsplan.api.v1.extractAndValidateUUIDParameter
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.db.domain.toResponse
import no.nav.syfo.oppfolgingsplan.db.domain.toSykmeldtOppfolgingsplanOverviewResponse
import no.nav.syfo.oppfolgingsplan.domain.Fodselsnummer
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.pdfgen.PdfGenService
import no.nav.syfo.texas.bearerToken
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.util.logger
import java.util.UUID

private val NINE_DIGIT_ORG_NUMBER = Regex("^\\d{9}$")

fun Route.registerSykmeldtOppfolgingsplanApiV1(
    texasHttpClient: TexasHttpClient,
    oppfolgingsplanService: OppfolgingsplanService,
    pdfGenService: PdfGenService,
    foresporselService: ForesporselService,
) {
    val logger = logger()

    suspend fun tryToGetPersistedOppfolgingsplanByUuid(
        uuid: UUID,
    ): PersistedOppfolgingsplan = oppfolgingsplanService.getPersistedOppfolgingsplanByUuid(uuid)

    fun checkIfOppfolgingsplanBelongsToSykmeldt(
        oppfolgingsplan: PersistedOppfolgingsplan,
        sykmeldtFnr: Fodselsnummer,
    ) {
        if (oppfolgingsplan.sykmeldtFnr != sykmeldtFnr.value) {
            logger.error("Oppfolgingsplan with uuid: ${oppfolgingsplan.uuid} does not belong to logged in user")
            throw ApiErrorException.NotFound("Oppfolgingsplan not found")
        }
    }

    route("/oppfolgingsplaner") {
        install(AddSykmeldtBrukerFnrAttributePlugin) {
            this.texasHttpClient = texasHttpClient
        }

        /**
         * Gir et subsett av felter for alle oppfolgingsplaner for sykmeldt.
         * Tiltenkt for oversiktsvisning.
         */
        get("/oversikt") {
            val brukerFnr = call.attributes[CALL_ATTRIBUTE_SYKMELDT_BRUKER_FODSELSNUMMER]
            val userToken = call.bearerToken()
                ?: throw ApiErrorException.Unauthorized("Missing bearer token")
            val persistedPlaner = oppfolgingsplanService.getPersistedOppfolgingsplanListBy(brukerFnr.value)
            val oppfolgingsplaner = persistedPlaner.toSykmeldtOppfolgingsplanOverviewResponse()
            val sykmeldteArbeidsforhold = foresporselService.getSykmeldteArbeidsforhold(
                sykmeldtFnr = brukerFnr.value,
                userToken = userToken,
                eksisterendePlaner = persistedPlaner,
            )

            call.respond(
                HttpStatusCode.OK,
                oppfolgingsplaner.copy(sykmeldteArbeidsforhold = sykmeldteArbeidsforhold),
            )
        }

        post("/be-om-plan") {
            val brukerFnr = call.attributes[CALL_ATTRIBUTE_SYKMELDT_BRUKER_FODSELSNUMMER]
            val userToken = call.bearerToken()
                ?: throw ApiErrorException.Unauthorized("Missing bearer token")
            val request = try {
                call.receive<BeOmPlanRequest>()
            } catch (e: Exception) {
                throw ApiErrorException.BadRequest("Invalid request body: ${e.message}", e)
            }

            if (!request.organisasjonsnummer.matches(NINE_DIGIT_ORG_NUMBER)) {
                throw ApiErrorException.BadRequest("organisasjonsnummer must be 9 digits")
            }

            foresporselService.beOmPlan(
                sykmeldtFnr = brukerFnr.value,
                organisasjonsnummer = request.organisasjonsnummer,
                userToken = userToken,
            )

            call.respond(HttpStatusCode.Created)
        }

        /**
         * Gir en komplett oppfolgingsplan.
         */
        get("/{uuid}") {
            val uuid = call.parameters.extractAndValidateUUIDParameter()

            val persistedOppfolgingsplan = tryToGetPersistedOppfolgingsplanByUuid(uuid)

            val brukerFnr = call.attributes[CALL_ATTRIBUTE_SYKMELDT_BRUKER_FODSELSNUMMER]
            checkIfOppfolgingsplanBelongsToSykmeldt(persistedOppfolgingsplan, brukerFnr)

            call.respond(HttpStatusCode.OK, persistedOppfolgingsplan.toResponse(false))
        }

        get("/{uuid}/pdf") {
            val uuid = call.parameters.extractAndValidateUUIDParameter()

            val oppfolgingsplan = tryToGetPersistedOppfolgingsplanByUuid(uuid)

            val brukerFnr = call.attributes[CALL_ATTRIBUTE_SYKMELDT_BRUKER_FODSELSNUMMER]
            checkIfOppfolgingsplanBelongsToSykmeldt(oppfolgingsplan, brukerFnr)

            val pdfByteArray = pdfGenService.generatePdf(oppfolgingsplan)
                ?: throw ApiErrorException.InternalServerError("An error occurred while generating pdf")

            call.response.status(HttpStatusCode.OK)
            call.response.headers.append(HttpHeaders.ContentType, "application/pdf")
            call.respond<ByteArray>(pdfByteArray)
        }
    }
}
