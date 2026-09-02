package no.nav.syfo.oppfolgingsplan.api.v1

import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import no.nav.syfo.application.Environment
import no.nav.syfo.application.auth.ClientAuthorizationPlugin
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.dokarkiv.DokarkivService
import no.nav.syfo.isdialogmelding.IsDialogmeldingService
import no.nav.syfo.istilgangskontroll.IsTilgangskontrollService
import no.nav.syfo.oppfolgingsplan.api.v1.arbeidsgiver.registerArbeidsgiverOppfolgingsplanApiV1
import no.nav.syfo.oppfolgingsplan.api.v1.arbeidsgiver.registerArbeidsgiverOppfolgingsplanUtkastApiV1
import no.nav.syfo.oppfolgingsplan.api.v1.arbeidsgiver.registerArbeidsgiverUnntaksvurderingApiV1
import no.nav.syfo.oppfolgingsplan.api.v1.sykmeldt.registerSykmeldtOppfolgingsplanApiV1
import no.nav.syfo.oppfolgingsplan.api.v1.veileder.registerVeilederOppfolgingsplanApiV1
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.oppfolgingsplan.service.UnntaksvurderingService
import no.nav.syfo.pdfgen.PdfGenService
import no.nav.syfo.sykmelding.db.SykmeldingsperiodeRepository
import no.nav.syfo.texas.TexasAzureADAuthPlugin
import no.nav.syfo.texas.TexasTokenXAuthPlugin
import no.nav.syfo.texas.client.TexasHttpClient
import java.time.Clock
import java.time.ZoneId

@Suppress("LongParameterList")
fun Route.registerApiV1(
    dineSykmeldteService: DineSykmeldteService,
    texasHttpClient: TexasHttpClient,
    oppfolgingsplanService: OppfolgingsplanService,
    unntaksvurderingService: UnntaksvurderingService,
    pdfGenService: PdfGenService,
    isDialogmeldingService: IsDialogmeldingService,
    isTilgangskontrollService: IsTilgangskontrollService,
    dokarkivService: DokarkivService,
    environment: Environment,
    sykmeldingsperiodeRepository: SykmeldingsperiodeRepository,
    sykmeldtOverviewClock: Clock = Clock.system(ZoneId.of("Europe/Oslo")),
) {
    route("/api/v1/arbeidsgiver") {
        install(TexasTokenXAuthPlugin) {
            client = texasHttpClient
        }
        install(ClientAuthorizationPlugin) {
            allowedClientId = environment.syfoOppfolgingsplanFrontendClientId
        }
        registerArbeidsgiverOppfolgingsplanApiV1(
            dineSykmeldteService,
            dokarkivService,
            texasHttpClient,
            oppfolgingsplanService,
            pdfGenService,
            isDialogmeldingService,
        )
        registerArbeidsgiverOppfolgingsplanUtkastApiV1(
            dineSykmeldteService,
            texasHttpClient,
            oppfolgingsplanService,
        )
        registerArbeidsgiverUnntaksvurderingApiV1(
            dineSykmeldteService,
            texasHttpClient,
            unntaksvurderingService,
        )
    }
    route("/api/v1/sykmeldt") {
        install(TexasTokenXAuthPlugin) {
            client = texasHttpClient
        }
        install(ClientAuthorizationPlugin) {
            allowedClientId = environment.syfoOppfolgingsplanFrontendClientId
        }
        registerSykmeldtOppfolgingsplanApiV1(
            texasHttpClient,
            oppfolgingsplanService,
            unntaksvurderingService,
            pdfGenService,
            sykmeldingsperiodeRepository,
            sykmeldtOverviewClock,
        )
    }
    route("/api/v1/veileder") {
        install(TexasAzureADAuthPlugin) {
            client = texasHttpClient
        }
        install(ClientAuthorizationPlugin) {
            allowedClientId = environment.syfomodiapersonClientId
        }
        registerVeilederOppfolgingsplanApiV1(
            texasHttpClient,
            oppfolgingsplanService,
            unntaksvurderingService,
            isTilgangskontrollService,
            pdfGenService,
        )
    }
}
