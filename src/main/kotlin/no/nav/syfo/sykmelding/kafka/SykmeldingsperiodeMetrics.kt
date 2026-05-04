package no.nav.syfo.sykmelding.kafka

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

val COUNT_SYKMELDING_CONSUMED: Counter = Counter.builder("${METRICS_NS}_sykmelding_consumed")
    .description("Counts the number of sykmeldingsperioder stored from Kafka")
    .register(METRICS_REGISTRY)
val COUNT_SYKMELDING_TOMBSTONE: Counter = Counter.builder("${METRICS_NS}_sykmelding_tombstone")
    .description("Counts the number of sykmelding tombstones processed from Kafka")
    .register(METRICS_REGISTRY)
val COUNT_SYKMELDING_DESERIALIZATION_ERROR: Counter = Counter.builder("${METRICS_NS}_sykmelding_deserialization_error")
    .description("Counts the number of sykmelding Kafka messages that failed deserialization (poison pills)")
    .register(METRICS_REGISTRY)
val COUNT_SYKMELDING_RUNTIME_ERROR: Counter = Counter.builder("${METRICS_NS}_sykmelding_runtime_error")
    .description("Counts transient consumer runtime errors (connection, commit, etc.)")
    .register(METRICS_REGISTRY)
