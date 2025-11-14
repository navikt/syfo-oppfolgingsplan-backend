package no.nav.syfo.oppfolgingsplan.api.v1.veileder

import no.nav.syfo.oppfolgingsplan.db.domain.PersistedOppfolgingsplan
import java.time.Instant
import java.time.LocalDate
import java.util.*

data class OppfolgingsplanVeileder(
    val uuid: UUID,
    val fnr: String,
    val deltMedNavTidspunkt: Instant,
    val virksomhetsnummer: String,
    val opprettet: Instant,
    val sistEndret: Instant,
    val evalueringsdato: LocalDate,
) {
    companion object {
        fun from(item: PersistedOppfolgingsplan): OppfolgingsplanVeileder {
            with(item) {
                require(deltMedVeilederTidspunkt != null) {
                    "Oppfolgingsplan ${uuid} is not shared with veileder"
                }
                val sisteEndre = listOfNotNull(
                    createdAt,
                    deltMedVeilederTidspunkt,
                    deltMedLegeTidspunkt
                ).max()
                return OppfolgingsplanVeileder(
                    uuid = uuid,
                    fnr = sykmeldtFnr,
                    virksomhetsnummer = organisasjonsnummer,
                    opprettet = createdAt,
                    deltMedNavTidspunkt = deltMedVeilederTidspunkt,
                    sistEndret = sisteEndre,
                    evalueringsdato = evalueringsdato,
                )
            }
        }
    }
}
