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
    fun ApplicationCall.requireVeilederToken(): String = principal<BrukerPrincipal>()?.token
        ?: throw ApiErrorException.Unauthorized("No user principal found in request")

    suspend fun ApplicationCall.receiveSykmeldtFnr(): Fodselsnummer {
        val request = try {
            receive<SykmeldtReadRequest>()
        } catch (e: Exception) {
            throw ApiErrorException.BadRequest("Request is missing sykmeldtFnr: ${e.message}", e)
        }
        return Fodselsnummer(value = request.sykmeldtFnr)
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
            val token = call.requireVeilederToken()
            val sykmeldtFnr = call.receiveSykmeldtFnr()
            validateTilgangToSykmeldt(
                sykmeldtFnr = sykmeldtFnr,
                token = token,
            )
            val oppfolgingsplaner = oppfolgingsplanService.getPersistedOppfolgingsplanListBy(
                sykmeldtFnr = sykmeldtFnr.value,
                inkluderSkjulte = true,
            ).toListOppfolgingsplanVeileder()

            call.respond(HttpStatusCode.OK, oppfolgingsplaner)
        }

        get("/{uuid}") {
            val uuid = call.parameters.extractAndValidateUUIDParameter()
            val token = call.requireVeilederToken()

            val oppfolgingsplan = tryToGetOppfolgingsplanByUuid(uuid)
            validateTilgangToSykmeldt(
                sykmeldtFnr = Fodselsnummer(value = oppfolgingsplan.sykmeldtFnr),
                token = token,
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
            val token = call.requireVeilederToken()
            val sykmeldtFnr = call.receiveSykmeldtFnr()
            validateTilgangToSykmeldt(
                sykmeldtFnr = sykmeldtFnr,
                token = token,
            )

            val unntaksvurderinger = unntaksvurderingService
                .getPersistedUnntaksvurderingerForSykmeldt(sykmeldtFnr.value)
                .map(UnntaksvurderingVeileder::from)

            call.respond(HttpStatusCode.OK, UnntaksvurderingerVeilederResponse(unntaksvurderinger))
        }
    }
}

const val NAV_PERSONIDENT_HEADER = "nav-personident"
