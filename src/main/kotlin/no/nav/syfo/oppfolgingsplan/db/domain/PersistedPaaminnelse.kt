package no.nav.syfo.oppfolgingsplan.db.domain

import no.nav.syfo.oppfolgingsplan.dto.PaaminnelseStatus
import java.time.Instant

data class PersistedPaaminnelse(
    val organisasjonsnummer: String,
    val sykmeldtFnr: String,
    val bestilt: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun PersistedPaaminnelse?.toStatus(): PaaminnelseStatus = if (this?.bestilt == true) {
    PaaminnelseStatus.BESTILT
} else {
    PaaminnelseStatus.TILGJENGELIG
}
