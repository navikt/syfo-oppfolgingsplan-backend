package no.nav.syfo.oppfolgingsplan.dto

import no.nav.syfo.oppfolgingsplan.domain.EmployeeDetails
import no.nav.syfo.oppfolgingsplan.domain.OrganizationDetails
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.FormSnapshot
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class OppfolgingsplanResponseData(
    val id: UUID,
    val content: FormSnapshot,
    val evalueringsDato: LocalDate,
    val deltMedLegeTidspunkt: Instant? = null,
    val deltMedVeilederTidspunkt: Instant? = null,
    val ferdigstiltTidspunkt: Instant,
    val stillingstittel: String? = null,
    val stillingsprosent: BigDecimal? = null,
)

data class OppfolgingsplanResponse(
    val userHasEditAccess: Boolean,
    val organization: OrganizationDetails,
    val employee: EmployeeDetails,
    val oppfolgingsplan: OppfolgingsplanResponseData,
)

data class CreateOppfolgingsplanRequest(
    val content: FormSnapshot,
    val evalueringsdato: LocalDate,
)

data class DelMedLegeResponse(
    val deltMedLegeTidspunkt: Instant,
)

data class DelMedVeilederResponse(
    val deltMedVeilederTidspunkt: Instant,
)
