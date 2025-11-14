package no.nav.syfo.oppfolgingsplan.db.domain

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
        sykmeldtFnr = sykmeldtFnr,
        narmesteLederFnr = narmesteLederFnr,
        organisasjonsnummer = organisasjonsnummer,
        evalueringsdato = evalueringsdato,
        skalDelesMedLege = skalDelesMedLege,
        skalDelesMedVeileder = skalDelesMedVeileder,
        deltMedLegeTidspunkt = deltMedLegeTidspunkt,
        deltMedVeilederTidspunkt = deltMedVeilederTidspunkt,
        createdAt = createdAt,
    )
}

fun PersistedOppfolgingsplan.toResponse(canEditPlan: Boolean): OppfolgingsplanResponse {
    return OppfolgingsplanResponse(
        canEditPlan = canEditPlan,
        uuid = uuid,
        sykmeldtFnr = sykmeldtFnr,
        narmesteLederFnr = narmesteLederFnr,
        organisasjonsnummer = organisasjonsnummer,
        evalueringsdato = evalueringsdato,
        skalDelesMedLege = skalDelesMedLege,
        skalDelesMedVeileder = skalDelesMedVeileder,
        deltMedLegeTidspunkt = deltMedLegeTidspunkt,
        deltMedVeilederTidspunkt = deltMedVeilederTidspunkt,
        createdAt = createdAt,
        sykmeldtFullName = sykmeldtFullName,
        narmesteLederId = narmesteLederId,
        narmesteLederFullName = narmesteLederFullName,
        organisasjonsnavn = organisasjonsnavn,
        content = content,
        utkastCreatedAt = utkastCreatedAt,
        sendtTilArkivportenTidspunkt = sendtTilArkivportenTidspunkt,
    )
}
