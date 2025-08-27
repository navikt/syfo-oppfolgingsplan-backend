package no.nav.syfo.oppfolgingsplan.api.v1.veilder

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import java.util.UUID
import no.nav.syfo.application.exception.InternalServerErrorException
import no.nav.syfo.oppfolgingsplan.api.v1.extractAndValidateUUIDParameter
import no.nav.syfo.oppfolgingsplan.db.PersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.oppfolgingsplan.service.toListOppfolginsplanVeiler
import no.nav.syfo.pdfgen.PdfGenService
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.util.logger

fun Route.registerVeilderOppfolgingsplanApiV1(
    texasHttpClient: TexasHttpClient,
    oppfolgingsplanService: OppfolgingsplanService,
    pdfGenService: PdfGenService,
) {
    route("/internad/veileder/oppfolgingsplaner") {
        install(AuthorizeVeilederAccessToSykmeldtPlugin) {
            this.texasHttpClient = texasHttpClient
//            this.dineSykmeldteService = dineSykmeldteService
        }
        fun tryToGetOppfolgingsplanByUuid(
            uuid: UUID,
        ): PersistedOppfolgingsplan =
            oppfolgingsplanService.getOppfolgingsplanByUuid(uuid) ?: run {
                throw NotFoundException("Oppfolgingsplan not found for uuid: $uuid")
            }
//        fun checkIfOppfolgingsplanBelongsToSykmeldt(sykmeldtFnr: String, oppfolgingsplan: PersistedOppfolgingsplan) {
//            if (oppfolgingsplan.sykmeldtFnr != sykmeldtFnr) {
//                logger.error("Oppfolgingsplan with uuid: ${oppfolgingsplan.uuid} does not belong to sykmeldt with fnr: $sykmeldtFnr")
//                throw ("Oppfolgingsplan not found")
//            }
//            // TODO sjekk om veileder har tilgang til sykmeldt
//        }

        get {
            val sykmeldtFnr = call.request.headers[NAV_PERSONIDENT_HEADER]
                ?: throw BadRequestException("Missing $NAV_PERSONIDENT_HEADER header")

            val oppfolgingsplaner =
                oppfolgingsplanService.getOppfolginsplanOverviewFor(sykmeldtFnr).toListOppfolginsplanVeiler()

            call.respond(HttpStatusCode.OK, oppfolgingsplaner)
        }

        get("/{uuid}") {
            val uuid = call.parameters.extractAndValidateUUIDParameter()

            val oppfolgingsplan = tryToGetOppfolgingsplanByUuid(uuid)
            val pdfByteArray = pdfGenService.generatePdf(oppfolgingsplan)
                ?: throw InternalServerErrorException("An error occurred while generating pdf")

            call.response.status(HttpStatusCode.OK)
            call.response.headers.append(HttpHeaders.ContentType, "application/pdf")
            call.respond<ByteArray>(pdfByteArray)
        }
    }
}
