package no.nav.syfo.oppfolgingsplan.dto

enum class PaaminnelseStatus {
    SKJULT,
    TILGJENGELIG,
    BESTILT,
}

data class PaaminnelseStatusDto(
    val status: PaaminnelseStatus,
)
