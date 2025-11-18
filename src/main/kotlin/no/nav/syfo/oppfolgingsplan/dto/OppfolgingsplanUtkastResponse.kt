package no.nav.syfo.oppfolgingsplan.dto

import no.nav.syfo.oppfolgingsplan.domain.EmployeeDetails
import no.nav.syfo.oppfolgingsplan.domain.OrganizationDetails
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.FormSnapshot
import java.time.Instant
import java.time.LocalDate

data class UtkastResponseData(
    val content: FormSnapshot?,
    val sistLagretTidspunkt: Instant,
)

data class OppfolgingsplanUtkastResponse(
    val userHasEditAccess: Boolean,
    val organization: OrganizationDetails,
    val employee: EmployeeDetails,
    val utkast: UtkastResponseData?,
)

data class CreateUtkastRequest(
    val content: FormSnapshot?,
    val evalueringsdato: LocalDate?,
)
