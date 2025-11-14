package no.nav.syfo.oppfolgingsplan.dto

import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.FormSnapshot
import java.time.Instant
import java.time.LocalDate
import java.util.*

data class OppfolgingsplanResponse(
    val canEditPlan: Boolean,
    val uuid: UUID,
    val sykmeldtFnr: String,
    val sykmeldtFullName: String,
    val narmesteLederId: String,
    val narmesteLederFnr: String,
    val narmesteLederFullName: String?,
    val organisasjonsnummer: String,
    val organisasjonsnavn: String?,
    val content: FormSnapshot,
    val evalueringsdato: LocalDate,
    val skalDelesMedLege: Boolean,
    val skalDelesMedVeileder: Boolean,
    val deltMedLegeTidspunkt: Instant? = null,
    val deltMedVeilederTidspunkt: Instant? = null,
    val utkastCreatedAt: Instant? = null,
    val createdAt: Instant,
    val sendtTilArkivportenTidspunkt: Instant? = null,
)

data class CreateOppfolgingsplanRequest(
    val content: FormSnapshot,
    val evalueringsdato: LocalDate,
    val skalDelesMedLege: Boolean,
    val skalDelesMedVeileder: Boolean,
    val deltMedLegeTidspunkt: Instant? = null,
    val deltMedVeilederTidspunkt: Instant? = null,
)
