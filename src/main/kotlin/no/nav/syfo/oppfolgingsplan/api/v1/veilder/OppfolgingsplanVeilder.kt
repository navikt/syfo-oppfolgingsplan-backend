package no.nav.syfo.oppfolgingsplan.api.v1.veilder

import java.util.UUID
import java.time.Instant
import no.nav.syfo.oppfolgingsplan.dto.OppfolgingsplanMetadata

data class OppfolgingsplanVeilder(
    val uuid: UUID,
    val fnr: String,
    val deltMedNavTidspunkt: Instant,
    val virksomhetsnummer: String,
    val opprettet: Instant,
    val sistEndret: Instant
) {
    companion object {
        fun from(item: OppfolgingsplanMetadata): OppfolgingsplanVeilder {
            with(item) {
                require(deltMedVeilederTidspunkt != null) {
                    "Oppfolgingsplan ${uuid} is not shared with veileder"
                }
                val sisteEndre = listOfNotNull(
                    createdAt,
                    deltMedVeilederTidspunkt,
                    deltMedLegeTidspunkt
                ).max()
                return OppfolgingsplanVeilder(
                    uuid = uuid,
                    fnr = sykmeldtFnr,
                    virksomhetsnummer = organisasjonsnummer,
                    opprettet = createdAt,
                    deltMedNavTidspunkt = deltMedVeilederTidspunkt,
                    sistEndret = sisteEndre
                )
            }
        }
    }
}
