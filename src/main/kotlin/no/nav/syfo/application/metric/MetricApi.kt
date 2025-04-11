package no.nav.syfo.application.metric

import io.ktor.server.response.*
import io.ktor.server.routing.*

const val podMetricsPath = "/internal/metrics"

fun Routing.registerMetricApi() {
    get(podMetricsPath) {
        call.respondText(METRICS_REGISTRY.scrape())
    }
}
