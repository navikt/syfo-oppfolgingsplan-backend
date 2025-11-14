package no.nav.syfo.oppfolgingsplan.dto

import java.time.Instant
import java.time.LocalDate
import java.util.*


data class UtkastMetadata(
    val sykmeldtFnr: String,
    val narmesteLederFnr: String,
    val organisasjonsnummer: String,
    val evalueringsdato: LocalDate?,
    val sistLagret: Instant,
)

data class OppfolgingsplanMetadata(
    val uuid: UUID,
    val sykmeldtFnr: String,
    val narmesteLederFnr: String,
    val organisasjonsnummer: String,
    val evalueringsdato: LocalDate,
    val skalDelesMedLege: Boolean,
    val skalDelesMedVeileder: Boolean,
    val deltMedLegeTidspunkt: Instant? = null,
    val deltMedVeilederTidspunkt: Instant? = null,
    val createdAt: Instant,
)

data class OppfolgingsplanOverviewResponse(
    val canEditPlan: Boolean,
    val organisasjonsnavn: String,
    val sykmeldtNavn: String,
    val sykmeldtFnr: String,
    val utkast: UtkastMetadata?,
    val oppfolgingsplan: OppfolgingsplanMetadata?,
    val previousOppfolgingsplaner: List<OppfolgingsplanMetadata>,
)

data class SykmeldtOppfolgingsplanOverview(
    val oppfolgingsplaner: List<OppfolgingsplanMetadata>,
    val previousOppfolgingsplaner: List<OppfolgingsplanMetadata>,
)