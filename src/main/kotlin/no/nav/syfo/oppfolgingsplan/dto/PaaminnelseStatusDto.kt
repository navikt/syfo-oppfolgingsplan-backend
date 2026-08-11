package no.nav.syfo.oppfolgingsplan.dto

import no.nav.syfo.oppfolgingsplan.model.Paaminnelse
import no.nav.syfo.oppfolgingsplan.model.PaaminnelseStatus

data class PaaminnelseStatusDto(
    val status: PaaminnelseStatus,
)

fun Paaminnelse.toDTO(): PaaminnelseStatusDto {
     return PaaminnelseStatusDto(
         status = this.status,
     )
 }
