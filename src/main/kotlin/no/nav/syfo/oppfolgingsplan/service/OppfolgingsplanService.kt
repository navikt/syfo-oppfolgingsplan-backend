package no.nav.syfo.oppfolgingsplan.service

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.oppfolgingsplan.db.persistOppfolgingsplanAndDeleteUtkast
import no.nav.syfo.oppfolgingsplan.domain.Oppfolgingsplan

class OppfolgingsplanService(
    private val database: DatabaseInterface,
) {

    fun persistOppfolgingsplan(narmesteLederId: String, oppfolgingsplan: Oppfolgingsplan) {
        database.persistOppfolgingsplanAndDeleteUtkast(narmesteLederId, oppfolgingsplan)
    }
}