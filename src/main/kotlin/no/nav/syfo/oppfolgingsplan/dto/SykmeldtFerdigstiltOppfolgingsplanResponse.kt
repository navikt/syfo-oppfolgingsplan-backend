package no.nav.syfo.oppfolgingsplan.dto

import no.nav.syfo.oppfolgingsplan.domain.OrganizationDetails
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.FormSnapshot
import java.time.Instant
import java.time.LocalDate
import java.util.*

data class SykmeldtFerdigstiltOppfolgingsplanResponseData(
    val id: UUID,
    val content: FormSnapshot,
    val evalueringsDato: LocalDate,
    val deltMedLegeTidspunkt: Instant? = null,
    val deltMedVeilederTidspunkt: Instant? = null,
    val ferdigstiltTidspunkt: Instant,
)

data class SykmeldtFerdigstiltOppfolgingsplanResponse(
    val organization: OrganizationDetails,
    val oppfolgingsplan: SykmeldtFerdigstiltOppfolgingsplanResponseData,
)
