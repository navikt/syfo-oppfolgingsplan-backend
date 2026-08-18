package no.nav.syfo.oppfolgingsplan.db.domain

import no.nav.syfo.oppfolgingsplan.domain.OrganizationDetails
import no.nav.syfo.oppfolgingsplan.dto.FerdigstiltPlanHendelse
import no.nav.syfo.oppfolgingsplan.dto.PlanIkkeNodvendigHendelse
import no.nav.syfo.oppfolgingsplan.dto.SykmeldtOppfolgingsplanHendelse
import no.nav.syfo.oppfolgingsplan.dto.SykmeldtOppfolgingsplanOverviewResponse
import no.nav.syfo.oppfolgingsplan.dto.SykmeldtVirksomhetsoversikt
import no.nav.syfo.oppfolgingsplan.dto.UnntaksvurderingMetadata
import java.time.Instant

fun List<PersistedOppfolgingsplan>.toSykmeldtOppfolgingsplanOverviewResponse(
    unntaksvurderinger: List<UnntaksvurderingMetadata>,
): SykmeldtOppfolgingsplanOverviewResponse {
    val planhendelser = map { it.toSykmeldtHendelse() }
    val unntakshendelser = unntaksvurderinger.map { it.toSykmeldtHendelse() }

    val virksomheter = (planhendelser + unntakshendelser)
        .groupBy { it.organization.orgNumber }
        .values
        .map { it.toVirksomhetsoversikt() }
        .sortedWith(virksomhetsoversiktComparator)
        .map { it.oversikt }

    return SykmeldtOppfolgingsplanOverviewResponse(virksomheter)
}

private fun PersistedOppfolgingsplan.toSykmeldtHendelse() = HendelseMedOrganisasjon(
    organization = OrganizationDetails(
        orgNumber = organisasjonsnummer,
        orgName = organisasjonsnavn,
    ),
    tidspunkt = createdAt,
    hendelse = FerdigstiltPlanHendelse(
        id = uuid,
        evalueringsDato = evalueringsdato,
        deltMedLegeTidspunkt = deltMedLegeTidspunkt,
        deltMedVeilederTidspunkt = deltMedVeilederTidspunkt,
        ferdigstiltTidspunkt = createdAt,
        stillingstittel = stillingstittel,
        stillingsprosent = stillingsprosent,
    ),
)

private fun UnntaksvurderingMetadata.toSykmeldtHendelse() = HendelseMedOrganisasjon(
    organization = organization,
    tidspunkt = meldtTidspunkt,
    hendelse = PlanIkkeNodvendigHendelse(
        id = id,
        meldtTidspunkt = meldtTidspunkt,
        meldtAv = meldtAv,
    ),
)

private fun List<HendelseMedOrganisasjon>.toVirksomhetsoversikt(): SorterbarVirksomhetsoversikt {
    val sorterteHendelser = sortedWith(hendelseComparator)
    val nyesteHendelse = sorterteHendelser.first()
    return SorterbarVirksomhetsoversikt(
        oversikt = SykmeldtVirksomhetsoversikt(
            organization = nyesteHendelse.organization,
            oppfolgingsplanhendelser = sorterteHendelser.map { it.hendelse },
        ),
        nyesteHendelse = nyesteHendelse,
    )
}

private data class HendelseMedOrganisasjon(
    val organization: OrganizationDetails,
    val tidspunkt: Instant,
    val hendelse: SykmeldtOppfolgingsplanHendelse,
)

private data class SorterbarVirksomhetsoversikt(
    val oversikt: SykmeldtVirksomhetsoversikt,
    val nyesteHendelse: HendelseMedOrganisasjon,
)

private val hendelseComparator =
    compareByDescending<HendelseMedOrganisasjon> { it.tidspunkt }
        .thenByDescending { it.hendelse.id.toString() }

private val virksomhetsoversiktComparator =
    compareByDescending<SorterbarVirksomhetsoversikt> { it.nyesteHendelse.tidspunkt }
        .thenByDescending { it.nyesteHendelse.hendelse.id.toString() }
