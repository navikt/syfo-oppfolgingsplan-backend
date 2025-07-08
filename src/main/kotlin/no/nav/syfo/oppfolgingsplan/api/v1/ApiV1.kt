package no.nav.syfo.oppfolgingsplan.api.v1

import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.oppfolgingsplan.api.v1.arbeidsgiver.registerArbeidsgiverOppfolgingsplanApiV1
import no.nav.syfo.oppfolgingsplan.api.v1.arbeidsgiver.registerArbeidsgiverOppfolgingsplanUtkastApiV1
import no.nav.syfo.oppfolgingsplan.api.v1.sykemeldt.registerSykemeldtOppfolgingsplanApiV1
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.texas.TexasAuthPlugin
import no.nav.syfo.texas.client.TexasHttpClient

fun Route.registerApiV1(
    dineSykmeldteService: DineSykmeldteService,
    texasHttpClient: TexasHttpClient,
    oppfolgingsplanService: OppfolgingsplanService
) {
    route("/api/v1") {
        install(TexasAuthPlugin) {
            client = texasHttpClient
        }
        registerArbeidsgiverOppfolgingsplanApiV1(
            dineSykmeldteService,
            texasHttpClient,
            oppfolgingsplanService
        )
        registerArbeidsgiverOppfolgingsplanUtkastApiV1(
            dineSykmeldteService,
            texasHttpClient,
            oppfolgingsplanService
        )
        registerSykemeldtOppfolgingsplanApiV1(
            texasHttpClient,
            oppfolgingsplanService,
        )
    }
}
