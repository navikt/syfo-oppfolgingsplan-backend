package no.nav.syfo.oppfolgingsplan.outbox

import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val OPPFOLGINGSPLAN_EVALUERING_PAAMINNELSE_OUTBOX =
    "${METRICS_NS}_oppfolgingsplan_evaluering_paaminnelse_outbox"

private const val METRIC_TAG_CHANNEL = "channel"
private const val METRIC_TAG_OUTCOME = "outcome"
private const val METRIC_OUTCOME_CREATED = "created"
private const val METRIC_OUTCOME_SUPERSEDED = "superseded"
private const val METRIC_OUTCOME_SOURCE_NO_LONGER_ELIGIBLE = "source_no_longer_eligible"

object OppfolgingsplanEvalueringPaaminnelseOutboxMetrics {
    fun incrementCreated(
        createdCountByChannel: Map<OppfolgingsplanOutboxMessageType, Int>,
    ) {
        createdCountByChannel.forEach { (messageType, count) ->
            increment(messageType, count, METRIC_OUTCOME_CREATED)
        }
    }

    fun incrementSuperseded(
        supersededCountByChannel: Map<OppfolgingsplanOutboxMessageType, Int>,
    ) {
        supersededCountByChannel.forEach { (messageType, count) ->
            increment(messageType, count, METRIC_OUTCOME_SUPERSEDED)
        }
    }

    fun incrementSourceNoLongerEligible(
        cancelledCountByChannel: Map<OppfolgingsplanOutboxMessageType, Int>,
    ) {
        cancelledCountByChannel.forEach { (messageType, count) ->
            increment(messageType, count, METRIC_OUTCOME_SOURCE_NO_LONGER_ELIGIBLE)
        }
    }

    private fun increment(
        messageType: OppfolgingsplanOutboxMessageType,
        count: Int,
        outcome: String,
    ) {
        if (count <= 0) return

        val channel = requireNotNull(messageType.channelMetricLabel) {
            "Missing channel metric label for message type ${messageType.value}"
        }
        METRICS_REGISTRY.counter(
            OPPFOLGINGSPLAN_EVALUERING_PAAMINNELSE_OUTBOX,
            METRIC_TAG_CHANNEL,
            channel,
            METRIC_TAG_OUTCOME,
            outcome,
        ).increment(count.toDouble())
    }
}
