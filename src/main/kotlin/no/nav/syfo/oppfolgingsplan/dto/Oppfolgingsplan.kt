package no.nav.syfo.oppfolgingsplan.dto

import com.fasterxml.jackson.databind.JsonNode
import no.nav.syfo.oppfolgingsplan.db.PersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.db.PersistedOppfolgingsplanUtkast
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
data class SykmeldtOppfolgingsplanOverview(
    val oppfolgingsplaner: List<OppfolgingsplanMetadata>,
    val previousOppfolgingsplaner: List<OppfolgingsplanMetadata>,
)

fun PersistedOppfolgingsplanUtkast.mapToUtkastMetadata(): UtkastMetadata {
    return UtkastMetadata(
        uuid = uuid,
        sykmeldtFnr = sykmeldtFnr,
        narmesteLederFnr = narmesteLederFnr,
        orgnummer = orgnummer,
        sluttdato = sluttdato,
    )
}

fun PersistedOppfolgingsplan.mapToOppfolgingsplanMetadata(): OppfolgingsplanMetadata {
    return OppfolgingsplanMetadata(
        uuid = uuid,
        sykmeldtFnr = sykmeldtFnr,
        narmesteLederFnr = narmesteLederFnr,
        orgnummer = orgnummer,
        sluttdato = sluttdato,
        skalDelesMedLege = skalDelesMedLege,
        skalDelesMedVeileder = skalDelesMedVeileder,
        deltMedLegeTidspunkt = deltMedLegeTidspunkt,
        deltMedVeilederTidspunkt = deltMedVeilederTidspunkt,
        createdAt = createdAt,
    )
}
