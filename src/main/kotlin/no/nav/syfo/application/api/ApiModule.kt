package no.nav.syfo.application.api


import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.metric.registerMetricApi
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.oppfolgingsplan.api.v1.registerApiV1
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.plugins.installCallId
import no.nav.syfo.plugins.installContentNegotiation
import no.nav.syfo.plugins.installStatusPages
import no.nav.syfo.texas.client.TexasHttpClient
import org.koin.ktor.ext.inject
import registerPodApi
import kotlin.getValue

fun Application.configureRouting() {
    val applicationState by inject<ApplicationState>()
    val database by inject<DatabaseInterface>()
    val texasHttpClient by inject<TexasHttpClient>()
    val dineSykmeldteService by inject<DineSykmeldteService>()
    val oppfolgingsplanService by inject<OppfolgingsplanService>()

    installCallId()
    installContentNegotiation()
    installStatusPages()

    routing {
        registerPodApi(applicationState, database)
        registerMetricApi()
        registerApiV1(
            dineSykmeldteService = dineSykmeldteService,
            texasHttpClient = texasHttpClient,
            oppfolgingsplanService = oppfolgingsplanService
        )
    }
}
