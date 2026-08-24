package no.nav.syfo.oppfolgingsplan.api.v1.veileder

import no.nav.syfo.oppfolgingsplan.db.domain.PersistedUnntaksvurdering
import java.time.Instant
import java.util.UUID

data class UnntaksvurderingerVeilederResponse(
    val unntaksvurderinger: List<UnntaksvurderingVeileder>,
)

data class UnntaksvurderingVeileder(
    val uuid: UUID,
    val fnr: String,
    val organisasjonsnummer: String,
    val organisasjonsnavn: String?,
    val meldtTidspunkt: Instant,
) {
    companion object {
        fun from(item: PersistedUnntaksvurdering): UnntaksvurderingVeileder = UnntaksvurderingVeileder(
            uuid = item.uuid,
            fnr = item.sykmeldtFnr,
            organisasjonsnummer = item.organisasjonsnummer,
            organisasjonsnavn = item.organisasjonsnavn,
            meldtTidspunkt = item.createdAt,
        )
    }
}
