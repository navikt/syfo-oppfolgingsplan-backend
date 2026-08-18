package no.nav.syfo.oppfolgingsplan.dto

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.syfo.oppfolgingsplan.domain.OrganizationDetails
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class SykmeldtOppfolgingsplanOverviewResponse(
    val virksomheter: List<SykmeldtVirksomhetsoversikt>,
)

data class SykmeldtVirksomhetsoversikt(
    val organization: OrganizationDetails,
    val oppfolgingsplanhendelser: List<SykmeldtOppfolgingsplanHendelse>,
)

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = FerdigstiltPlanHendelse::class, name = "FERDIGSTILT_PLAN"),
    JsonSubTypes.Type(value = PlanIkkeNodvendigHendelse::class, name = "PLAN_IKKE_NODVENDIG"),
)
sealed interface SykmeldtOppfolgingsplanHendelse {
    val id: UUID
}

data class FerdigstiltPlanHendelse(
    override val id: UUID,
    val evalueringsDato: LocalDate,
    val deltMedLegeTidspunkt: Instant? = null,
    val deltMedVeilederTidspunkt: Instant? = null,
    val ferdigstiltTidspunkt: Instant,
    val stillingstittel: String? = null,
    val stillingsprosent: BigDecimal? = null,
) : SykmeldtOppfolgingsplanHendelse

data class PlanIkkeNodvendigHendelse(
    override val id: UUID,
    val meldtTidspunkt: Instant,
    val meldtAv: MeldtAv,
) : SykmeldtOppfolgingsplanHendelse
