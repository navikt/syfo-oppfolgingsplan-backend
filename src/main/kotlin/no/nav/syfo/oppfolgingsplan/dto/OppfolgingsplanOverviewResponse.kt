package no.nav.syfo.oppfolgingsplan.dto

import no.nav.syfo.oppfolgingsplan.domain.EmployeeDetails
import no.nav.syfo.oppfolgingsplan.domain.OrganizationDetails
import java.time.Instant
import java.time.LocalDate
import java.util.*


data class UtkastMetadata(
    val updatedAt: Instant,
)

data class OppfolgingsplanMetadata(
    val uuid: UUID,
    val evalueringsdato: LocalDate,
    val deltMedLegeTidspunkt: Instant? = null,
    val deltMedVeilederTidspunkt: Instant? = null,
    val createdAt: Instant,
)

data class OppfolgingsplanOverviewResponse(
    val userHasEditAccess: Boolean,
    val organization: OrganizationDetails,
    val employee: EmployeeDetails,
    val utkast: UtkastMetadata?,
    val oppfolgingsplan: OppfolgingsplanMetadata?,
    val previousOppfolgingsplaner: List<OppfolgingsplanMetadata>,
)

data class SykmeldtOppfolgingsplanOverview(
    val oppfolgingsplaner: List<OppfolgingsplanMetadata>,
    val previousOppfolgingsplaner: List<OppfolgingsplanMetadata>,
)
