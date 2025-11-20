package no.nav.syfo.oppfolgingsplan.dto

import no.nav.syfo.oppfolgingsplan.domain.EmployeeDetails
import no.nav.syfo.oppfolgingsplan.domain.OrganizationDetails
import java.time.Instant
import java.time.LocalDate
import java.util.*


data class UtkastMetadata(
    val sistLagretTidspunkt: Instant,
)

data class OversiktResponseData(
    val utkast: UtkastMetadata?,
    val aktivPlan: OppfolgingsplanMetadata?,
    val tidligerePlaner: List<OppfolgingsplanMetadata>,
)

data class OppfolgingsplanMetadata(
    val id: UUID,
    val evalueringsDato: LocalDate,
    val deltMedLegeTidspunkt: Instant? = null,
    val deltMedVeilederTidspunkt: Instant? = null,
    val ferdigstiltTidspunkt: Instant,
)

data class OppfolgingsplanOverviewResponse(
    val userHasEditAccess: Boolean,
    val organization: OrganizationDetails,
    val employee: EmployeeDetails,
    val oversikt: OversiktResponseData
)

data class SykmeldtOppfolgingsplanOverview(
    val aktiveOppfolgingsplaner: List<OppfolgingsplanMetadata>,
    val tidligerePlaner: List<OppfolgingsplanMetadata>,
)
