package no.nav.syfo.oppfolgingsplan.outbox

import no.nav.syfo.application.outbox.domain.OutboxMessageType

enum class OppfolgingsplanOutboxMessageType(
    override val value: String,
) : OutboxMessageType {
    CREATED("OPPFOLGINGSPLAN_CREATED"),
    EVALUERING_PAAMINNELSE_MIN_SIDE_ARBEIDSGIVER(
        "OPPFOLGINGSPLAN_EVALUERING_PAAMINNELSE_MIN_SIDE_ARBEIDSGIVER",
    ),
    EVALUERING_PAAMINNELSE_DINE_SYKMELDTE(
        "OPPFOLGINGSPLAN_EVALUERING_PAAMINNELSE_DINE_SYKMELDTE",
    ),
    PAAMINNELSE_ARBEIDSGIVER("PAAMINNELSE_OPPFOLGINGSPLAN_ARBEIDSGIVER"),
    PAAMINNELSE_DINE_SYKMELDTE("PAAMINNELSE_OPPFOLGINGSPLAN_DINE_SYKMELDTE"),
    ;

    val channelMetricLabel: String?
        get() = when (this) {
            CREATED -> null
            PAAMINNELSE_ARBEIDSGIVER -> null
            PAAMINNELSE_DINE_SYKMELDTE -> null
            EVALUERING_PAAMINNELSE_MIN_SIDE_ARBEIDSGIVER -> "min_side_arbeidsgiver"
            EVALUERING_PAAMINNELSE_DINE_SYKMELDTE -> "dine_sykmeldte"
        }

    companion object {
        val evalueringPaaminnelseTypes = listOf(
            EVALUERING_PAAMINNELSE_MIN_SIDE_ARBEIDSGIVER,
            EVALUERING_PAAMINNELSE_DINE_SYKMELDTE,
        )
    }
}
