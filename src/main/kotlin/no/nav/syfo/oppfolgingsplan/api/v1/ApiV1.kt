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
import no.nav.syfo.oppfolgingsplan.api.v1.veilder.registerVeilderOppfolgingsplanApiV1
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.pdfgen.PdfGenService
import no.nav.syfo.texas.TexasAuthPlugin
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
    route("/api/v1") {
        install(TexasAuthPlugin) {
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
        registerSykmeldtOppfolgingsplanApiV1(
            texasHttpClient,
            oppfolgingsplanService,
            pdfGenService
        )
        registerVeilderOppfolgingsplanApiV1(
            texasHttpClient,
            oppfolgingsplanService,
            isTilgangskontrollService,
            pdfGenService
        )
    }
}
