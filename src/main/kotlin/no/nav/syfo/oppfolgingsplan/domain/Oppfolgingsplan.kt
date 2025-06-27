package no.nav.syfo.oppfolgingsplan.domain

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class Oppfolgingsplan (
    val uuid: UUID? = null,
    val sykmeldtFnr: String,
    val narmesteLederFnr: String,
    val orgnummer: String,
    val content: JsonNode,
    val sluttdato: LocalDate,
    val skalDelesMedLege: Boolean,
    val skalDelesMedVeileder: Boolean,
    val deltMedLegeTidspunkt: Instant? = null,
    val deltMedVeilederTidspunkt: Instant? = null,
)

data class OppfolgingsplanUtkast(
    val uuid: UUID? = null,
    val sykmeldtFnr: String,
    val narmesteLederFnr: String,
    val orgnummer: String,
    val content: JsonNode?,
    val sluttdato: LocalDate?,
)