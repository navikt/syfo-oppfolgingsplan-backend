package no.nav.syfo.oppfolgingsplan.db.domain

import no.nav.syfo.oppfolgingsplan.dto.OppfolgingsplanUtkastResponse
import no.nav.syfo.oppfolgingsplan.dto.UtkastMetadata
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.FormSnapshot
import java.time.Instant
import java.time.LocalDate
import java.util.*

data class PersistedOppfolgingsplanUtkast(
    val uuid: UUID,
    val sykmeldtFnr: String,
    val narmesteLederId: String,
    val narmesteLederFnr: String,
    val organisasjonsnummer: String,
    val content: FormSnapshot?,
    val evalueringsdato: LocalDate?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun PersistedOppfolgingsplanUtkast.toUtkastMetadata(): UtkastMetadata {
    return UtkastMetadata(
        sykmeldtFnr = sykmeldtFnr,
        narmesteLederFnr = narmesteLederFnr,
        organisasjonsnummer = organisasjonsnummer,
        evalueringsdato = evalueringsdato,
        sistLagret = updatedAt
    )
}

fun PersistedOppfolgingsplanUtkast.toResponse(canEdit: Boolean): OppfolgingsplanUtkastResponse {
    return OppfolgingsplanUtkastResponse(
        canEditPlan = canEdit,
        sykmeldtFnr = sykmeldtFnr,
        narmesteLederId = narmesteLederId,
        narmesteLederFnr = narmesteLederFnr,
        organisasjonsnummer = organisasjonsnummer,
        content = content,
        evalueringsdato = evalueringsdato,
        createdAt = createdAt,
        sistLagret = updatedAt,
    )
}