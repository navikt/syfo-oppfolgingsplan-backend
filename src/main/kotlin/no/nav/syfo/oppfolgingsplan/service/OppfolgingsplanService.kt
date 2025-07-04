package no.nav.syfo.oppfolgingsplan.service

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.oppfolgingsplan.db.PersistedOppfolgingsplanUtkast
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanUtkastBy
import no.nav.syfo.oppfolgingsplan.db.persistOppfolgingsplanAndDeleteUtkast
import no.nav.syfo.oppfolgingsplan.db.upsertOppfolgingsplanUtkast
import no.nav.syfo.oppfolgingsplan.dto.Oppfolgingsplan
import no.nav.syfo.oppfolgingsplan.dto.OppfolgingsplanUtkast
import no.nav.syfo.varsel.EsyfovarselProducer
import no.nav.syfo.varsel.domain.ArbeidstakerHendelse
import no.nav.syfo.varsel.domain.HendelseType

class OppfolgingsplanService(
    private val database: DatabaseInterface,
    private val esyfovarselProducer: EsyfovarselProducer,
) {

    fun persistOppfolgingsplan(narmesteLederId: String, oppfolgingsplan: Oppfolgingsplan) {
        database.persistOppfolgingsplanAndDeleteUtkast(narmesteLederId, oppfolgingsplan)
    }

    fun persistOppfolgingsplanUtkast(narmesteLederId: String, utkast: OppfolgingsplanUtkast) {
        database.upsertOppfolgingsplanUtkast(narmesteLederId, utkast)
    }

    fun getOppfolgingsplanUtkast(narmesteLederId: String): PersistedOppfolgingsplanUtkast? {
        return database.findOppfolgingsplanUtkastBy(narmesteLederId)
    }

    fun produceVarsel(oppfolgingsplan: Oppfolgingsplan) {
        val hendelse = ArbeidstakerHendelse(
            type = HendelseType.SM_OPPFOLGINGSPLAN_OPPRETTET,
            ferdigstill = false,
            arbeidstakerFnr = oppfolgingsplan.sykmeldtFnr,
            data = null,
            orgnummer = oppfolgingsplan.orgnummer,
        )
        esyfovarselProducer.sendVarselToEsyfovarsel(hendelse)
    }
}
