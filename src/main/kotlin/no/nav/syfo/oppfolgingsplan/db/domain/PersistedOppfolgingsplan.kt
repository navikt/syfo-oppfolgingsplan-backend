package no.nav.syfo.oppfolgingsplan.db.domain

import no.nav.syfo.oppfolgingsplan.domain.EmployeeDetails
import no.nav.syfo.oppfolgingsplan.domain.OrganizationDetails
import no.nav.syfo.oppfolgingsplan.dto.OppfolgingsplanMetadata
import no.nav.syfo.oppfolgingsplan.dto.OppfolgingsplanResponse
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.FormSnapshot
import java.time.Instant
import java.time.LocalDate
import java.util.*

data class PersistedOppfolgingsplan(
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

fun PersistedOppfolgingsplan.toOppfolgingsplanMetadata(): OppfolgingsplanMetadata {
    return OppfolgingsplanMetadata(
        uuid = uuid,
        evalueringsdato = evalueringsdato,
        deltMedLegeTidspunkt = deltMedLegeTidspunkt,
        deltMedVeilederTidspunkt = deltMedVeilederTidspunkt,
        createdAt = createdAt,
    )
}

fun PersistedOppfolgingsplan.toResponse(canEditPlan: Boolean): OppfolgingsplanResponse {
    return OppfolgingsplanResponse(
        userHasEditAccess = canEditPlan,
        organization = OrganizationDetails(
            orgNumber = organisasjonsnummer,
            orgName = organisasjonsnavn,
        ),
        employee = EmployeeDetails(
            fnr = sykmeldtFnr,
            name = sykmeldtFullName,
        ),
        evalueringsdato = evalueringsdato,
        deltMedLegeTidspunkt = deltMedLegeTidspunkt,
        deltMedVeilederTidspunkt = deltMedVeilederTidspunkt,
        content = content,
        ferdistiltTidspunkt = createdAt,
    )
}
