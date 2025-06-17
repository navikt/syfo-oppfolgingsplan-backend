package no.nav.syfo.application.api


import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import no.nav.syfo.application.metric.registerMetricApi
import no.nav.syfo.oppfolgingsplan.registerOppfolgingsplanApi
import registerPodApi

fun Application.apiModule() {
    installCallId()
    installContentNegotiation()
    installStatusPages()

    routing {
        registerPodApi()
        registerMetricApi()
        registerOppfolgingsplanApi()
    }
}
