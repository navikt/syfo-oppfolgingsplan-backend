package no.nav.syfo.foresporsel.domain

import java.time.Instant

data class SykmeldtArbeidsforhold(
    val organisasjonsnummer: String,
    val organisasjonsnavn: String?,
    val narmesteLederNavn: String?,
    val foresporselStatus: ForesporselStatus,
    val foresporselTidspunkt: Instant?,
)
