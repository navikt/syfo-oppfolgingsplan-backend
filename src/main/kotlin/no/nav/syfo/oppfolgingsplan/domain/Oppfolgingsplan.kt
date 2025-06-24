package no.nav.syfo.oppfolgingsplan.domain

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class Oppfolgingsplan(
    val sykmeldtFnr: String,
    val narmesteLederFnr: String,
    val orgnummer: String,
    val content: String,
    val sluttdato: LocalDate,
    val shouldShareWithGP: Boolean,
    val shouldShareWithNav: Boolean,
)

data class OppfolgingsplanUtkast(
    val sykmeldtFnr: String,
    val narmesteLederFnr: String,
    val orgnummer: String,
    val content: String?,
    val sluttdato: LocalDate?,
)