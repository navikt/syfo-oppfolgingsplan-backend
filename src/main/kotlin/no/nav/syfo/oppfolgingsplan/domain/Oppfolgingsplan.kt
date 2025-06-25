package no.nav.syfo.oppfolgingsplan.domain

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Oppfolgingsplan(
    val sykmeldtFnr: String,
    val narmesteLederFnr: String,
    val orgnummer: String,
    val content: JsonElement,
    val sluttdato: LocalDate,
    val skalDelesMedLege: Boolean,
    val skalDelesMedVeileder: Boolean,
)

@Serializable
data class OppfolgingsplanUtkast(
    val sykmeldtFnr: String,
    val narmesteLederFnr: String,
    val orgnummer: String,
    val content: JsonElement?,
    val sluttdato: LocalDate?,
)