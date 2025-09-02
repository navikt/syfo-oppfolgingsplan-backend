package no.nav.syfo.oppfolgingsplan.api.v1.veilder

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.auth.principal
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.intercept
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.*
import no.nav.syfo.application.auth.BrukerPrincipal
import no.nav.syfo.application.exception.ForbiddenException
import no.nav.syfo.application.exception.InternalServerErrorException
import no.nav.syfo.application.exception.UnauthorizedException
import no.nav.syfo.istilgangskontroll.IsTilgangskontrollService
import no.nav.syfo.oppfolgingsplan.api.v1.extractAndValidateUUIDParameter
import no.nav.syfo.oppfolgingsplan.db.PersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.domain.Fodselsnummer
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.oppfolgingsplan.service.toListOppfolginsplanVeiler
import no.nav.syfo.pdfgen.PdfGenService
import no.nav.syfo.texas.client.TexasHttpClient

@Suppress("ThrowsCount")
fun Route.registerVeilderOppfolgingsplanApiV1(
    texasHttpClient: TexasHttpClient,
    oppfolgingsplanService: OppfolgingsplanService,
    isTilgangskontrollService: IsTilgangskontrollService,
    pdfGenService: PdfGenService,
) {
    route("/oppfolgingsplaner") {
        fun tryToGetOppfolgingsplanByUuid(
            uuid: UUID,
        ): PersistedOppfolgingsplan = oppfolgingsplanService.getOppfolgingsplanByUuid(uuid).let {
            if (it == null || it.deltMedVeilederTidspunkt == null) {
                throw NotFoundException("Oppfolgingsplan not found for uuid: $uuid")
            } else {
                it
            }
        }

        suspend fun validateTilgangToSykmeldt(
            sykmeldtFnr: Fodselsnummer, token: String
        ) {
            val tilgang = isTilgangskontrollService.harTilgangTilSykmeldt(
                sykmeldtFnr, texasHttpClient.exchangeTokenForIsTilgangskontroll(token).accessToken
            )
            if (!tilgang) {
                throw ForbiddenException("Veileder does not have access to sykmeldt")
            }
        }

        post("/query") {
            val innloggetBruker = call.principal<BrukerPrincipal>()
                ?: throw UnauthorizedException("No user principal found in request")
            val sykmeldtFnr = try {
                call.receive<OppfolginsplanerReadRequest>().sykmeldtFnr
            } catch (e: Exception) {
                throw BadRequestException("Request is missing sykmeldtFnr: ${e.message}", e)
            }
            validateTilgangToSykmeldt(
                sykmeldtFnr = Fodselsnummer(value = sykmeldtFnr),
                token = innloggetBruker.token,
            )
            val oppfolgingsplaner =
                oppfolgingsplanService.getOppfolginsplanOverviewFor(sykmeldtFnr).toListOppfolginsplanVeiler()

            call.respond(HttpStatusCode.OK, oppfolgingsplaner)
        }

        get("/{uuid}") {
            val uuid = call.parameters.extractAndValidateUUIDParameter()
            val innloggetBruker = call.principal<BrukerPrincipal>()
                ?: throw UnauthorizedException("No user principal found in request")

            val oppfolgingsplan = tryToGetOppfolgingsplanByUuid(uuid)
            validateTilgangToSykmeldt(
                sykmeldtFnr = Fodselsnummer(value = oppfolgingsplan.sykmeldtFnr),
                token = innloggetBruker.token,
            )
            val pdfByteArray = pdfGenService.generatePdf(oppfolgingsplan)
                ?: throw InternalServerErrorException("An error occurred while generating pdf")

            call.response.status(HttpStatusCode.OK)
            call.response.headers.append(HttpHeaders.ContentType, "application/pdf")
            call.respond<ByteArray>(pdfByteArray)
        }
    }
}
const val NAV_PERSONIDENT_HEADER = "nav-personident"
