package no.nav.syfo.oppfolgingsplan.db.domain

import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.dinesykmeldte.client.getOrganizationName
import no.nav.syfo.oppfolgingsplan.domain.EmployeeDetails
import no.nav.syfo.oppfolgingsplan.domain.OrganizationDetails
import no.nav.syfo.oppfolgingsplan.dto.OppfolgingsplanUtkastResponse
import no.nav.syfo.oppfolgingsplan.dto.UtkastMetadata
import no.nav.syfo.oppfolgingsplan.dto.UtkastResponseData
import java.time.Instant
import java.util.*

data class PersistedOppfolgingsplanUtkast(
    val uuid: UUID,
    val sykmeldtFnr: String,
    val narmesteLederId: String,
    val narmesteLederFnr: String,
    val organisasjonsnummer: String,
    val content: Map<String, String?>,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun PersistedOppfolgingsplanUtkast.toUtkastMetadata(): UtkastMetadata {
    return UtkastMetadata(
        sistLagretTidspunkt = updatedAt
    )
}

fun PersistedOppfolgingsplanUtkast?.toArbeidsgiverFerdigstiltPlanResponse(sykmeldt: Sykmeldt): OppfolgingsplanUtkastResponse {
    return OppfolgingsplanUtkastResponse(
        userHasEditAccess = sykmeldt.aktivSykmelding == true,
        organization = OrganizationDetails(
            orgNumber = sykmeldt.orgnummer,
            orgName = sykmeldt.getOrganizationName(),
        ),
        employee = EmployeeDetails(
            fnr = sykmeldt.fnr,
            name = sykmeldt.navn,
        ),
        utkast = this?.let {
            UtkastResponseData(
                content = it.content,
                sistLagretTidspunkt = it.updatedAt,
            )
        },
    )
}
