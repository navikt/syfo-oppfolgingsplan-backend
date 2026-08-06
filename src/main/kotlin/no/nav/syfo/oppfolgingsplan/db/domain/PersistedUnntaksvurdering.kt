package no.nav.syfo.oppfolgingsplan.db.domain

import java.time.Instant
import java.util.UUID

data class PersistedUnntaksvurdering(
    val uuid: UUID,
    val sykmeldtFnr: String,
    val organisasjonsnummer: String,
    val narmesteLederFnr: String,
    val narmesteLederFullName: String?,
    val createdAt: Instant,
    val skjultFra: Instant? = null,
)
