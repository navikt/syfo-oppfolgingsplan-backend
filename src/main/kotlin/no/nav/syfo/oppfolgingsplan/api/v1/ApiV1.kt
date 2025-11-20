package no.nav.syfo.oppfolgingsplan.api.v1

import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.dokarkiv.DokarkivService
import no.nav.syfo.isdialogmelding.IsDialogmeldingService
import no.nav.syfo.istilgangskontroll.IsTilgangskontrollService
import no.nav.syfo.oppfolgingsplan.api.v1.arbeidsgiver.registerArbeidsgiverOppfolgingsplanApiV1
import no.nav.syfo.oppfolgingsplan.api.v1.arbeidsgiver.registerArbeidsgiverOppfolgingsplanUtkastApiV1
import no.nav.syfo.oppfolgingsplan.api.v1.sykmeldt.registerSykmeldtOppfolgingsplanApiV1
import no.nav.syfo.oppfolgingsplan.api.v1.veileder.registerVeilderOppfolgingsplanApiV1
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.pdfgen.PdfGenService
import no.nav.syfo.texas.TexasAzureADAuthPlugin
import no.nav.syfo.texas.TexasTokenXAuthPlugin
import no.nav.syfo.texas.client.TexasHttpClient

@Suppress("LongParameterList")
fun Route.registerApiV1(
    dineSykmeldteService: DineSykmeldteService,
    texasHttpClient: TexasHttpClient,
    oppfolgingsplanService: OppfolgingsplanService,
    pdfGenService: PdfGenService,
    isDialogmeldingService: IsDialogmeldingService,
    isTilgangskontrollService: IsTilgangskontrollService,
    dokarkivService: DokarkivService,
) {
    route("/api/v1/arbeidsgiver") {
        install(TexasTokenXAuthPlugin) {
            client = texasHttpClient
        }
        registerArbeidsgiverOppfolgingsplanApiV1(
            dineSykmeldteService,
            dokarkivService,
            texasHttpClient,
            oppfolgingsplanService,
            pdfGenService,
            isDialogmeldingService
        )
        registerArbeidsgiverOppfolgingsplanUtkastApiV1(
            dineSykmeldteService,
            texasHttpClient,
            oppfolgingsplanService
        )
    }
    route("/api/v1/sykmeldt") {
        install(TexasTokenXAuthPlugin) {
            client = texasHttpClient
        }
        registerSykmeldtOppfolgingsplanApiV1(
            texasHttpClient,
            oppfolgingsplanService,
            pdfGenService
        )
    }
    route("/api/v1/veileder") {
        install(TexasAzureADAuthPlugin) {
            client = texasHttpClient
        }
        registerVeilderOppfolgingsplanApiV1(
            texasHttpClient,
            oppfolgingsplanService,
            isTilgangskontrollService,
            pdfGenService
        )
    }
}
