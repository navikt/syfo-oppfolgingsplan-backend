package no.nav.syfo.modia.domain

import java.time.Instant

data class KFollowUpPlan(
    val uuid: String,
    val fodselsnummer: String,
    val virksomhetsnummer: String,
    val behovForBistandFraNav: Boolean,
    val opprettet: Int,
    val opprettetTimestamp: Instant,
)
