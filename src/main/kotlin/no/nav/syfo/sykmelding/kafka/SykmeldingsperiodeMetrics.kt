package no.nav.syfo.sykmelding.kafka

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

internal const val SYKMELDING_TERMINALLY_REJECTED_RECORDS_METRIC =
    "${METRICS_NS}_sykmelding_terminally_rejected_records"
internal const val SYKMELDING_DESERIALIZATION_RETRY_BATCH_ATTEMPTS_METRIC =
    "${METRICS_NS}_sykmelding_deserialization_retry_batch_attempts"

internal enum class SykmeldingsperiodeRejectionReason(
    val labelValue: String,
) {
    DESERIALIZATION("deserialization"),
    INVALID_TOMBSTONE("invalid_tombstone"),
}

class SykmeldingsperiodeRecordMetrics(
    registry: MeterRegistry = METRICS_REGISTRY,
) {
    private val terminallyRejectedCounters =
        SykmeldingsperiodeRejectionReason.entries.associateWith { reason ->
            Counter.builder(SYKMELDING_TERMINALLY_REJECTED_RECORDS_METRIC)
                .description("Counts sykmelding Kafka records permanently rejected after their offsets were committed")
                .tag("reason", reason.labelValue)
                .register(registry)
        }
    private val deserializationRetryBatchAttempts =
        Counter.builder(SYKMELDING_DESERIALIZATION_RETRY_BATCH_ATTEMPTS_METRIC)
            .description("Counts batch attempts where every record failed deserialization and offsets were not committed")
            .register(registry)

    internal fun recordTerminallyRejected(
        reason: SykmeldingsperiodeRejectionReason,
        count: Int,
    ) {
        if (count > 0) {
            terminallyRejectedCounters.getValue(reason).increment(count.toDouble())
        }
    }

    internal fun recordDeserializationRetryBatchAttempt() {
        deserializationRetryBatchAttempts.increment()
    }
}

val COUNT_SYKMELDING_CONSUMED: Counter = Counter.builder("${METRICS_NS}_sykmelding_consumed")
    .description("Counts the number of sykmeldingsperioder stored from Kafka")
    .register(METRICS_REGISTRY)
val COUNT_SYKMELDING_TOMBSTONE: Counter = Counter.builder("${METRICS_NS}_sykmelding_tombstone")
    .description("Counts the number of sykmelding tombstones processed from Kafka")
    .register(METRICS_REGISTRY)
val COUNT_SYKMELDING_DESERIALIZATION_ERROR: Counter = Counter.builder("${METRICS_NS}_sykmelding_deserialization_error")
    .description("Legacy attempt counter for sykmelding records that fail deserialization or have an invalid tombstone key")
    .register(METRICS_REGISTRY)
val COUNT_SYKMELDING_RUNTIME_ERROR: Counter = Counter.builder("${METRICS_NS}_sykmelding_runtime_error")
    .description("Counts transient consumer runtime errors (connection, commit, etc.)")
    .register(METRICS_REGISTRY)
