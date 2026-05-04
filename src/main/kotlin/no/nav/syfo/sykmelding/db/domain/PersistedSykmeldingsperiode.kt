package no.nav.syfo.sykmelding.db.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class PersistedSykmeldingsperiode(
    val id: UUID,
    val sykmeldtFnr: String,
    val organisasjonsnummer: String,
    val sykmeldingId: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val invalidatedAt: Instant?,
    val createdAt: Instant,
)
