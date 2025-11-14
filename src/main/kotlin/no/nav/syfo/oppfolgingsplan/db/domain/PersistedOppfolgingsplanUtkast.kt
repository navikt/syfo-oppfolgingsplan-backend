package no.nav.syfo.oppfolgingsplan.db.domain

import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.dinesykmeldte.client.getOrganizationName
import no.nav.syfo.oppfolgingsplan.domain.EmployeeDetails
import no.nav.syfo.oppfolgingsplan.domain.OrganizationDetails
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
        updatedAt = updatedAt
    )
}

fun PersistedOppfolgingsplanUtkast.toResponse(sykmeldt: Sykmeldt): OppfolgingsplanUtkastResponse {
    return OppfolgingsplanUtkastResponse(
        userHasEditAccess = sykmeldt.aktivSykmelding == true,
        organization = OrganizationDetails(
            orgNumber = organisasjonsnummer,
            orgName = sykmeldt.getOrganizationName(),
        ),
        employee = EmployeeDetails(
            fnr = sykmeldtFnr,
            name = sykmeldt.navn,
        ),
        content = content,
        evalueringsdato = evalueringsdato,
        createdAt = createdAt,
        sistLagret = updatedAt,
    )
}