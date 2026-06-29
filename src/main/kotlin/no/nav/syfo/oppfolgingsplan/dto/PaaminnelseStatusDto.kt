package no.nav.syfo.oppfolgingsplan.dto

import java.time.LocalDate

enum class PaaminnelseStatus {
    SKJULT,
    TILGJENGELIG,
    BESTILT,
}

data class PaaminnelseStatusDto(
    val status: PaaminnelseStatus,
    val synligFra: LocalDate? = null,
)
