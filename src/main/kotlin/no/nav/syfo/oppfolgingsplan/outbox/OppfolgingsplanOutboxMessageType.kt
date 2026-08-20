package no.nav.syfo.oppfolgingsplan.outbox

import no.nav.syfo.application.outbox.domain.OutboxMessageType

enum class OppfolgingsplanOutboxMessageType(
    override val value: String,
) : OutboxMessageType {
    CREATED("OPPFOLGINGSPLAN_CREATED"),
    PAAMINNELSE("PAAMINNELSE_OPPFOLGINGSPLAN"),
}
