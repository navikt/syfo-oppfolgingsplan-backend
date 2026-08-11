package no.nav.syfo.oppfolgingsplan.model

import java.time.LocalDate

enum class PaaminnelseStatus {
    SKJULT,
    TILGJENGELIG,
    BESTILT,
}

data class Paaminnelse(
    val status: PaaminnelseStatus,
    val forlopFom: LocalDate? = null,
)
