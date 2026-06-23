package no.nav.syfo.oppfolgingsplan.api.v1.narmesteleder

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.syfo.application.Environment
import no.nav.syfo.application.auth.ClientAuthorizationPlugin
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.oppfolgingsplan.api.v1.COUNT_PAAMINNELSE_AVBESTILT
import no.nav.syfo.oppfolgingsplan.api.v1.COUNT_PAAMINNELSE_BESTILT
import no.nav.syfo.oppfolgingsplan.api.v1.arbeidsgiver.AuthorizeLeaderAccessToSykmeldtPlugin
import no.nav.syfo.oppfolgingsplan.api.v1.arbeidsgiver.CALL_ATTRIBUTE_SYKMELDT
import no.nav.syfo.oppfolgingsplan.service.PaaminnelseService
import no.nav.syfo.texas.TexasTokenXAuthPlugin
import no.nav.syfo.texas.client.TexasHttpClient

fun Route.registerPaaminnelseApi(
    dineSykmeldteService: DineSykmeldteService,
    texasHttpClient: TexasHttpClient,
    paaminnelseService: PaaminnelseService,
    environment: Environment,
) {
    route("/api/v1/narmesteleder/{narmesteLederId}/oppfolgingsplaner/paaminnelse") {
        install(TexasTokenXAuthPlugin) {
            client = texasHttpClient
        }
        install(ClientAuthorizationPlugin) {
            allowedClientId = environment.dinesykmeldteBackendClientId
        }
        install(AuthorizeLeaderAccessToSykmeldtPlugin) {
            this.texasHttpClient = texasHttpClient
            this.dineSykmeldteService = dineSykmeldteService
        }

        get {
            val sykmeldt = call.attributes[CALL_ATTRIBUTE_SYKMELDT]
            call.respond(
                HttpStatusCode.OK,
                paaminnelseService.getPaaminnelseStatus(sykmeldt),
            )
        }

        post {
            val sykmeldt = call.attributes[CALL_ATTRIBUTE_SYKMELDT]
            if (sykmeldt.aktivSykmelding == true) {
                val response = paaminnelseService.bestillPaaminnelse(
                    sykmeldt = sykmeldt,
                )

                COUNT_PAAMINNELSE_BESTILT.increment()
                call.respond(HttpStatusCode.OK, response)
            } else {
                throw BadRequestException("Kan ikke bestille påminnelse for sykmeldt uten aktiv sykmelding")
            }
        }

        delete {
            val sykmeldt = call.attributes[CALL_ATTRIBUTE_SYKMELDT]

            val response = paaminnelseService.avbestillPaaminnelse(
                sykmeldt = sykmeldt,
            )

            COUNT_PAAMINNELSE_AVBESTILT.increment()
            call.respond(HttpStatusCode.OK, response)
        }
    }
}
