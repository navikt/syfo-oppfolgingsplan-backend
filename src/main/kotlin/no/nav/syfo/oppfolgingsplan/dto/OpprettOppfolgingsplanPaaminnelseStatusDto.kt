package no.nav.syfo.oppfolgingsplan.dto

enum class OpprettOppfolgingsplanPaaminnelseStatus {
    SKJULT,
    TILGJENGELIG,
    BESTILT,
}

data class OpprettOppfolgingsplanPaaminnelseStatusDto(
    val status: OpprettOppfolgingsplanPaaminnelseStatus,
)
