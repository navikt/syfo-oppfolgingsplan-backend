package no.nav.syfo.oppfolgingsplan.api.v1.veilder

import java.time.LocalDateTime
import java.util.UUID

class OppfolgingsplanVeilder(
    val uuid: UUID,
    val fnr: String,
    val virksomhetsnummer: String,
    val opprettet: LocalDateTime,
    val deltMedNavTidspunkt: LocalDateTime
)
