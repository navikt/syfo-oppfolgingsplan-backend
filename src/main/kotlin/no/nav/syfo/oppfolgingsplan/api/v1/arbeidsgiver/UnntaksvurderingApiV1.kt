package no.nav.syfo.oppfolgingsplan.api.v1.arbeidsgiver

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.syfo.application.auth.BrukerPrincipal
import no.nav.syfo.application.exception.ApiErrorException
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.oppfolgingsplan.api.v1.COUNT_UNNTAKSVURDERING_CREATED
import no.nav.syfo.oppfolgingsplan.service.UnntaksvurderingService
import no.nav.syfo.texas.client.TexasHttpClient

fun Route.registerArbeidsgiverUnntaksvurderingApiV1(
    dineSykmeldteService: DineSykmeldteService,
    texasHttpClient: TexasHttpClient,
    unntaksvurderingService: UnntaksvurderingService,
) {
    route("/{narmesteLederId}/unntaksvurderinger") {
        install(AuthorizeLeaderAccessToSykmeldtPlugin) {
            this.texasHttpClient = texasHttpClient
            this.dineSykmeldteService = dineSykmeldteService
        }

        post {
            val innloggetBruker = call.principal<BrukerPrincipal>()
                ?: throw ApiErrorException.Unauthorized("No user principal found in request")

            val sykmeldt = call.attributes[CALL_ATTRIBUTE_SYKMELDT]

            if (sykmeldt.aktivSykmelding != true) {
                throw ApiErrorException.Forbidden(
                    "Cannot create unntaksvurdering for sykmeldt without active sykmelding",
                )
            }

            unntaksvurderingService.createUnntaksvurdering(
                innloggetBruker.ident,
                sykmeldt,
            )

            COUNT_UNNTAKSVURDERING_CREATED.increment()
            call.respond(HttpStatusCode.Created)
        }
    }
}
