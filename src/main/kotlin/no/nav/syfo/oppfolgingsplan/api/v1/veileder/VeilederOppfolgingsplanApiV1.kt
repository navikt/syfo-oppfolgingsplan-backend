package no.nav.syfo.oppfolgingsplan.api.v1.veileder

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.syfo.application.auth.BrukerPrincipal
import no.nav.syfo.application.exception.ApiErrorException
import no.nav.syfo.application.exception.PlanNotFoundException
import no.nav.syfo.istilgangskontroll.IsTilgangskontrollService
import no.nav.syfo.oppfolgingsplan.api.v1.extractAndValidateUUIDParameter
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.domain.Fodselsnummer
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.oppfolgingsplan.service.UnntaksvurderingService
import no.nav.syfo.oppfolgingsplan.service.toListOppfolgingsplanVeileder
import no.nav.syfo.pdfgen.PdfGenService
import no.nav.syfo.texas.client.TexasHttpClient
import java.util.UUID

@Suppress("ThrowsCount")
fun Route.registerVeilederOppfolgingsplanApiV1(
    texasHttpClient: TexasHttpClient,
    oppfolgingsplanService: OppfolgingsplanService,
    unntaksvurderingService: UnntaksvurderingService,
    isTilgangskontrollService: IsTilgangskontrollService,
    pdfGenService: PdfGenService,
) {
    suspend fun ApplicationCall.receiveVeilederSykmeldtQuery(): VeilederSykmeldtQuery {
        val innloggetBruker = principal<BrukerPrincipal>()
            ?: throw ApiErrorException.Unauthorized("No user principal found in request")
        val sykmeldtFnr = try {
            receive<SykmeldtReadRequest>().sykmeldtFnr
        } catch (e: Exception) {
            throw ApiErrorException.BadRequest("Request is missing sykmeldtFnr: ${e.message}", e)
        }
        return VeilederSykmeldtQuery(
            sykmeldtFnr = Fodselsnummer(value = sykmeldtFnr),
            token = innloggetBruker.token,
        )
    }

    suspend fun validateTilgangToSykmeldt(
        sykmeldtFnr: Fodselsnummer,
        token: String,
    ) {
        val tilgang = isTilgangskontrollService.harTilgangTilSykmeldt(
            sykmeldtFnr,
            texasHttpClient.exchangeTokenForIsTilgangskontroll(token).accessToken,
        )
        if (!tilgang) {
            throw ApiErrorException.Forbidden("Veileder does not have access to sykmeldt")
        }
    }

    route("/oppfolgingsplaner") {
        suspend fun tryToGetOppfolgingsplanByUuid(
            uuid: UUID,
        ): PersistedOppfolgingsplan = oppfolgingsplanService.getPersistedOppfolgingsplanByUuid(
            uuid = uuid,
            inkluderSkjulte = true,
        ).let {
            if (it.deltMedVeilederTidspunkt == null) {
                throw PlanNotFoundException("Oppfolgingsplan not found for uuid: $uuid")
            } else {
                it
            }
        }

        post("/query") {
            val query = call.receiveVeilederSykmeldtQuery()
            validateTilgangToSykmeldt(
                sykmeldtFnr = query.sykmeldtFnr,
                token = query.token,
            )
            val oppfolgingsplaner = oppfolgingsplanService.getPersistedOppfolgingsplanListBy(
                sykmeldtFnr = query.sykmeldtFnr.value,
                inkluderSkjulte = true,
            ).toListOppfolgingsplanVeileder()

            call.respond(HttpStatusCode.OK, oppfolgingsplaner)
        }

        get("/{uuid}") {
            val uuid = call.parameters.extractAndValidateUUIDParameter()
            val innloggetBruker = call.principal<BrukerPrincipal>()
                ?: throw ApiErrorException.Unauthorized("No user principal found in request")

            val oppfolgingsplan = tryToGetOppfolgingsplanByUuid(uuid)
            validateTilgangToSykmeldt(
                sykmeldtFnr = Fodselsnummer(value = oppfolgingsplan.sykmeldtFnr),
                token = innloggetBruker.token,
            )
            val pdfByteArray = pdfGenService.generatePdf(oppfolgingsplan)
                ?: throw ApiErrorException.InternalServerError("Could not generate pdf")

            call.response.status(HttpStatusCode.OK)
            call.response.headers.append(HttpHeaders.ContentType, "application/pdf")
            call.respond<ByteArray>(pdfByteArray)
        }
    }

    route("/unntaksvurderinger") {
        post("/query") {
            val query = call.receiveVeilederSykmeldtQuery()
            validateTilgangToSykmeldt(
                sykmeldtFnr = query.sykmeldtFnr,
                token = query.token,
            )

            val unntaksvurderinger = unntaksvurderingService
                .getPersistedUnntaksvurderingerForSykmeldt(query.sykmeldtFnr.value)
                .map(UnntaksvurderingVeileder::from)

            call.respond(HttpStatusCode.OK, UnntaksvurderingerVeilederResponse(unntaksvurderinger))
        }
    }
}

private data class VeilederSykmeldtQuery(
    val sykmeldtFnr: Fodselsnummer,
    val token: String,
)

const val NAV_PERSONIDENT_HEADER = "nav-personident"
