package no.nav.syfo.oppfolgingsplan.db.domain

import no.nav.syfo.oppfolgingsplan.model.PaaminnelseStatus
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class PersistedPaaminnelse(
    val uuid: UUID,
    val organisasjonsnummer: String,
    val sykmeldtFnr: String,
    val forlopFom: LocalDate,
    val bestilt: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun PersistedPaaminnelse?.toStatus(): PaaminnelseStatus = if (this?.bestilt == true) {
    PaaminnelseStatus.BESTILT
} else {
    PaaminnelseStatus.TILGJENGELIG
}
