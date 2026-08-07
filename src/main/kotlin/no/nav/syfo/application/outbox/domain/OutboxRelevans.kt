package no.nav.syfo.application.outbox.domain

sealed interface OutboxRelevans {
    data object Relevant : OutboxRelevans

    data object IkkeRelevant : OutboxRelevans

    data object Utsatt : OutboxRelevans
}
