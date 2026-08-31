package no.nav.syfo.oppfolgingsplan.db.domain

import java.time.Instant
import java.util.UUID

data class PersistedOpprettOppfolgingsplanPaaminnelse(
    val uuid: UUID,
    val organisasjonsnummer: String,
    val sykmeldtFnr: String,
    val bestilt: Boolean,
    val bestillingId: UUID,
    val sykmeldingsperiodeId: UUID,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun PersistedOpprettOppfolgingsplanPaaminnelse.isOpprettOppfolgingsplanPaaminnelseBestiltInCurrentSykemeldingsperiode(
    sykmeldingsperiodeId: UUID,
): Boolean = this.bestilt &&
    this.sykmeldingsperiodeId == sykmeldingsperiodeId
