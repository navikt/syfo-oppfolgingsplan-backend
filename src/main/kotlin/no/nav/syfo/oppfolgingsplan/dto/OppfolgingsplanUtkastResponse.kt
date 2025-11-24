package no.nav.syfo.oppfolgingsplan.dto

import no.nav.syfo.oppfolgingsplan.domain.EmployeeDetails
import no.nav.syfo.oppfolgingsplan.domain.OrganizationDetails
import java.time.Instant

data class UtkastResponseData(
    val content: Map<String, String?>,
    val sistLagretTidspunkt: Instant,
)

data class OppfolgingsplanUtkastResponse(
    val userHasEditAccess: Boolean,
    val organization: OrganizationDetails,
    val employee: EmployeeDetails,
    val utkast: UtkastResponseData?,
)

data class CreateUtkastRequest(
    val content: Map<String, String?>,
)
