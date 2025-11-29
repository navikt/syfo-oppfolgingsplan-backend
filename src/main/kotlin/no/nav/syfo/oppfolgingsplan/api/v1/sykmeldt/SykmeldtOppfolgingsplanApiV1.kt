package no.nav.syfo.oppfolgingsplan.api.v1.sykmeldt

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.syfo.application.exception.InternalServerErrorException
import no.nav.syfo.oppfolgingsplan.api.v1.extractAndValidateUUIDParameter
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.db.domain.toResponse
import no.nav.syfo.oppfolgingsplan.domain.Fodselsnummer
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.oppfolgingsplan.service.toSykmeldtOppfolgingsplanOverview
import no.nav.syfo.pdfgen.PdfGenService
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.util.logger
import java.util.*

fun Route.registerSykmeldtOppfolgingsplanApiV1(
    texasHttpClient: TexasHttpClient,
    oppfolgingsplanService: OppfolgingsplanService,
    pdfGenService: PdfGenService,
) {
    val logger = logger()

    fun tryToGetPersistedOppfolgingsplanByUuid(
        uuid: UUID,
    ): PersistedOppfolgingsplan =
        oppfolgingsplanService.getPersistedOppfolgingsplanByUuid(uuid) ?: run {
            throw NotFoundException("Oppfolgingsplan not found for uuid: $uuid")
        }

    fun checkIfOppfolgingsplanBelongsToSykmeldt(
        oppfolgingsplan: PersistedOppfolgingsplan,
        sykmeldtFnr: Fodselsnummer,
    ) {
        if (oppfolgingsplan.sykmeldtFnr != sykmeldtFnr.value) {
            logger.error("Oppfolgingsplan with uuid: ${oppfolgingsplan.uuid} does not belong to logged in user")
            throw NotFoundException("Oppfolgingsplan not found")
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
            val oppfolgingsplaner =
                oppfolgingsplanService
                    .getOppfolgingsplanOverviewFor(brukerFnr.value)
                    .toSykmeldtOppfolgingsplanOverview()

            call.respond(HttpStatusCode.OK, oppfolgingsplaner)
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
                ?: throw InternalServerErrorException("An error occurred while generating pdf")

            call.response.status(HttpStatusCode.OK)
            call.response.headers.append(HttpHeaders.ContentType, "application/pdf")
            call.respond<ByteArray>(pdfByteArray)
        }
    }
}
