package no.nav.syfo.varsel.budstikka.infrastructure

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

val COUNT_BUDSTIKKA_OPPFOLGINGSPLAN_VARSEL_PUBLISHED: Counter =
    Counter.builder("${METRICS_NS}_budstikka_oppfolgingsplan_varsel_published")
        .description("Counts published Budstikka notifications for created oppfolgingsplaner")
        .register(METRICS_REGISTRY)

val COUNT_BUDSTIKKA_OPPFOLGINGSPLAN_VARSEL_FAILED: Counter =
    Counter.builder("${METRICS_NS}_budstikka_oppfolgingsplan_varsel_failed")
        .description("Counts failed Budstikka notifications for created oppfolgingsplaner")
        .register(METRICS_REGISTRY)

val COUNT_BUDSTIKKA_OPPFOLGINGSPLAN_VARSEL_RETRIED: Counter =
    Counter.builder("${METRICS_NS}_budstikka_oppfolgingsplan_varsel_retried")
        .description("Counts Budstikka notifications published by the retry task")
        .register(METRICS_REGISTRY)
