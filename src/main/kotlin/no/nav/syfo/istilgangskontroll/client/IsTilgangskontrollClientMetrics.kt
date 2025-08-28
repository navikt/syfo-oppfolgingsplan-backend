package no.nav.syfo.istilgangskontroll.client

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val CALL_TILGANGSKONTROLL_PERSON_BASE = "${METRICS_NS}_call_tilgangskontroll_person"
const val CALL_TILGANGSKONTROLL_PERSON_SUCCESS = "${CALL_TILGANGSKONTROLL_PERSON_BASE}_success_count"
const val CALL_TILGANGSKONTROLL_PERSON_FAIL = "${CALL_TILGANGSKONTROLL_PERSON_BASE}_fail_count"
const val CALL_TILGANGSKONTROLL_PERSON_FORBIDDEN = "${CALL_TILGANGSKONTROLL_PERSON_BASE}_forbidden_count"

val COUNT_CALL_TILGANGSKONTROLL_PERSON_SUCCESS: Counter = Counter.builder(CALL_TILGANGSKONTROLL_PERSON_SUCCESS)
    .description("Counts the number of successful calls to istilgangskontroll - person")
    .register(METRICS_REGISTRY)
val COUNT_CALL_TILGANGSKONTROLL_PERSON_FAIL: Counter = Counter.builder(CALL_TILGANGSKONTROLL_PERSON_FAIL)
    .description("Counts the number of failed calls to istilgangskontroll - person")
    .register(METRICS_REGISTRY)
val COUNT_CALL_TILGANGSKONTROLL_PERSON_FORBIDDEN: Counter = Counter.builder(CALL_TILGANGSKONTROLL_PERSON_FORBIDDEN)
    .description("Counts the number of forbidden calls to istilgangskontroll - person")
    .register(METRICS_REGISTRY)
