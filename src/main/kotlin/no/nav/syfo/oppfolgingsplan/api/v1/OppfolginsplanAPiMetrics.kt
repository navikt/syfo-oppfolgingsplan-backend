package no.nav.syfo.oppfolgingsplan.api.v1

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val OPPFOLGINGSPLAN_CREATED = "${METRICS_NS}_oppfolgingsplan_created"
const val OPPFOLGINGSPLAN_SHARED_WITH_GP = "${METRICS_NS}_oppfolgingsplan_shared_with_gp"
const val OPPFOLGINGSPLAN_SHARED_WITH_NAV = "${METRICS_NS}_oppfolgingsplan_shared_with_nav"
const val OPPFOLGINGSPLAN_DRAFT_MANUALLY_DELETED = "${METRICS_NS}_oppfolgingsplan_draft_manually_deleted"

val COUNT_OPPFOLGINGSPLAN_CREATED: Counter = Counter.builder(OPPFOLGINGSPLAN_CREATED)
    .description("Counts the number of created oppfolgingsplans")
    .register(METRICS_REGISTRY)
val COUNT_OPPFOLGINGSPLAN_SHARED_WITH_GP: Counter = Counter.builder(OPPFOLGINGSPLAN_SHARED_WITH_GP)
    .description("Counts the number of oppfolgingsplans that are shared with a doctor")
    .register(METRICS_REGISTRY)
val COUNT_OPPFOLGINGSPLAN_SHARED_WITH_NAV: Counter = Counter.builder(OPPFOLGINGSPLAN_SHARED_WITH_NAV)
    .description("Counts the number of created oppfolgingsplans that are shared with NAV")
    .register(METRICS_REGISTRY)
val COUNT_OPPFOLGINGSPLAN_DRAFT_MANUALLY_DELETED: Counter = Counter.builder(OPPFOLGINGSPLAN_DRAFT_MANUALLY_DELETED)
    .description("Counts the number of drafts oppfolgingsplan manually deleted")
    .register(METRICS_REGISTRY)
