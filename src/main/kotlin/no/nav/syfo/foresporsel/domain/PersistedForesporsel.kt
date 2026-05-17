package no.nav.syfo.foresporsel.domain

import java.time.Instant
import java.util.UUID

data class PersistedForesporsel(
    val id: UUID,
    val sykmeldtFnr: String,
    val narmesteLederFnr: String,
    val organisasjonsnummer: String,
    val createdAt: Instant,
)
