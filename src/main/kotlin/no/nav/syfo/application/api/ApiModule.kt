package no.nav.syfo.application.api

import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.metric.registerMetricApi
import no.nav.syfo.oppfolgingsplan.registerOppfolgingsplanApi
import no.nav.syfo.texas.TexasHttpClient
import registerPodApi

fun Application.apiModule(
    applicationState: ApplicationState,
    database: DatabaseInterface,
    texasHttpClient: TexasHttpClient
) {
    installCallId()
    installContentNegotiation()
    installStatusPages()

    routing {
        registerPodApi(
            applicationState = applicationState,
            database = database
        )
        registerMetricApi()
        registerOppfolgingsplanApi(texasHttpClient)
    }
}
