package no.nav.syfo.oppfolgingsplan.dto

import no.nav.syfo.oppfolgingsplan.domain.EmployeeDetails
import no.nav.syfo.oppfolgingsplan.domain.OrganizationDetails
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class UtkastMetadata(
    val sistLagretTidspunkt: Instant,
    val utkastUtloperDato: Instant,
)

data class OversiktResponseData(
    val utkast: UtkastMetadata?,
    val aktivPlan: OppfolgingsplanMetadata?,
    val tidligerePlaner: List<OppfolgingsplanMetadata>,
    val unntaksvurderinger: List<UnntaksvurderingMetadata>,
    val gjeldendeStatus: GjeldendeStatus,
)

data class OppfolgingsplanMetadata(
    val id: UUID,
    val evalueringsDato: LocalDate,
    val deltMedLegeTidspunkt: Instant? = null,
    val deltMedVeilederTidspunkt: Instant? = null,
    val ferdigstiltTidspunkt: Instant,
    val stillingstittel: String? = null,
    val stillingsprosent: BigDecimal? = null,
    val organization: OrganizationDetails,
)

data class ArbeidsgiverOppfolgingsplanOverviewResponse(
    val userHasEditAccess: Boolean,
    val organization: OrganizationDetails,
    val employee: EmployeeDetails,
    val oversikt: OversiktResponseData,
)

enum class GjeldendeStatus {
    AKTIV_PLAN,
    UTKAST,
    IKKE_AKTUELT,
    INGEN,
}

enum class MeldtAvRolle {
    ARBEIDSGIVER,
}

data class MeldtAv(
    val navn: String?,
    val rolle: MeldtAvRolle,
)

data class UnntaksvurderingMetadata(
    val id: UUID,
    val meldtTidspunkt: Instant,
    val meldtAv: MeldtAv,
    val organization: OrganizationDetails,
)

fun utledGjeldendeStatus(
    utkast: UtkastMetadata?,
    aktivPlan: OppfolgingsplanMetadata?,
    unntaksvurderinger: List<UnntaksvurderingMetadata>,
): GjeldendeStatus = when {
    aktivPlan != null -> GjeldendeStatus.AKTIV_PLAN
    utkast != null -> GjeldendeStatus.UTKAST
    unntaksvurderinger.isNotEmpty() -> GjeldendeStatus.IKKE_AKTUELT
    else -> GjeldendeStatus.INGEN
}
