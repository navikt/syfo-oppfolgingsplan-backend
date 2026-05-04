package no.nav.syfo.sykmelding.db.domain

import java.time.LocalDate

data class SykmeldingsperiodeToStore(
    val sykmeldtFnr: String,
    val organisasjonsnummer: String,
    val sykmeldingId: String,
    val fom: LocalDate,
    val tom: LocalDate,
)
