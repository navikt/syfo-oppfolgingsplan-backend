package no.nav.syfo.oppfolgingsplan.dto

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateOppfolgingsplanRequest (
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

data class OppfolgingsplanMetadata (
    val uuid: UUID,
    val sykmeldtFnr: String,
    val narmesteLederFnr: String,
    val orgnummer: String,
    val sluttdato: LocalDate,
    val skalDelesMedLege: Boolean,
    val skalDelesMedVeileder: Boolean,
    val deltMedLegeTidspunkt: Instant? = null,
    val deltMedVeilederTidspunkt: Instant? = null,
    val createdAt: Instant,
)

data class CreateUtkastRequest(
    val sykmeldtFnr: String,
    val narmesteLederFnr: String,
    val orgnummer: String,
    val content: JsonNode?,
    val sluttdato: LocalDate?,
)

data class UtkastMetadata(
    val uuid: UUID,
    val sykmeldtFnr: String,
    val narmesteLederFnr: String,
    val orgnummer: String,
    val sluttdato: LocalDate?,
)

data class OppfolgingsplanOverview(
    val utkast: UtkastMetadata?,
    val oppfolgingsplan: OppfolgingsplanMetadata?,
    val previousOppfolgingsplaner: List<OppfolgingsplanMetadata>,
)