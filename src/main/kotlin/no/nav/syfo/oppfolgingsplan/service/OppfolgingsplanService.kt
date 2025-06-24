package no.nav.syfo.oppfolgingsplan.service

import no.nav.syfo.oppfolgingsplan.db.OppfolgingsplanDAO
import no.nav.syfo.oppfolgingsplan.domain.Oppfolgingsplan
import java.util.UUID

class OppfolgingsplanService(
    private val oppfolgingsplanDAO: OppfolgingsplanDAO,
) {

    suspend fun persistOppfolgingsplan(narmesteLederId: String, oppfolgingsplan: Oppfolgingsplan): UUID {
        return oppfolgingsplanDAO.persist(narmesteLederId, oppfolgingsplan)
    }
}