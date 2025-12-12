package no.nav.syfo.oppfolgingsplan.api.v1

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val OPPFOLGINSPLAN_CREATED = "${METRICS_NS}_oppfolginsplan_created"
const val OPPFOLGINSPLAN_SHARED_WITH_GP = "${METRICS_NS}_oppfolginsplan_shared_with_gp"
const val OPPFOLGINSPLAN_SHARED_WITH_NAV = "${METRICS_NS}_oppfolginsplan_shared_with_nav"

val COUNT_OPPFOLGINSPLAN_CREATED: Counter = Counter.builder(OPPFOLGINSPLAN_CREATED)
    .description("Counts the number of created oppfolginsplans")
    .register(METRICS_REGISTRY)
val COUNT_OPPFOLGINSPLAN_SHARED_WITH_GP: Counter = Counter.builder(OPPFOLGINSPLAN_SHARED_WITH_GP)
    .description("Counts the number of oppfolginsplans that are shared with a doctor")
    .register(METRICS_REGISTRY)
val COUNT_OPPFOLGINSPLAN_SHARED_WITH_NAV: Counter = Counter.builder(OPPFOLGINSPLAN_SHARED_WITH_NAV)
    .description("Counts the number of created oppfolginsplans")
    .register(METRICS_REGISTRY)
