package no.nav.syfo.oppfolgingsplan.db.domain

import no.nav.syfo.oppfolgingsplan.domain.OrganizationDetails
import no.nav.syfo.oppfolgingsplan.dto.MeldtAv
import no.nav.syfo.oppfolgingsplan.dto.MeldtAvRolle
import no.nav.syfo.oppfolgingsplan.dto.UnntaksvurderingMetadata
import java.time.Instant
import java.util.UUID

data class PersistedUnntaksvurdering(
    val uuid: UUID,
    val sykmeldtFnr: String,
    val organisasjonsnummer: String,
    val organisasjonsnavn: String?,
    val narmesteLederFnr: String,
    val narmesteLederFullName: String?,
    val createdAt: Instant,
    val skjultFra: Instant? = null,
)

/**
 * [organisasjonsnavnFallback] dekker rader persistert før organisasjonsnavn-kolonnen fantes;
 * den lagrede verdien er alltid førstevalget.
 */
fun PersistedUnntaksvurdering.toUnntaksvurderingMetadata(
    organisasjonsnavnFallback: String? = null,
): UnntaksvurderingMetadata = UnntaksvurderingMetadata(
    id = uuid,
    meldtTidspunkt = createdAt,
    meldtAv = MeldtAv(
        navn = narmesteLederFullName,
        rolle = MeldtAvRolle.ARBEIDSGIVER,
    ),
    organization = OrganizationDetails(
        orgNumber = organisasjonsnummer,
        orgName = organisasjonsnavn ?: organisasjonsnavnFallback,
    ),
)
