package no.nav.syfo.oppfolgingsplan.service

import java.time.LocalDate
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.oppfolgingsplan.db.PersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.db.PersistedOppfolgingsplanUtkast
import no.nav.syfo.oppfolgingsplan.db.findAllOppfolgingsplanerBy
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanBy
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanUtkastBy
import no.nav.syfo.oppfolgingsplan.db.persistOppfolgingsplanAndDeleteUtkast
import no.nav.syfo.oppfolgingsplan.db.upsertOppfolgingsplanUtkast
import no.nav.syfo.oppfolgingsplan.dto.CreateOppfolgingsplanRequest
import no.nav.syfo.oppfolgingsplan.dto.OppfolgingsplanOverview
import no.nav.syfo.oppfolgingsplan.dto.CreateUtkastRequest
import java.util.UUID
import no.nav.syfo.oppfolgingsplan.dto.SykmeldtOppfolgingsplanOverview
import no.nav.syfo.oppfolgingsplan.dto.mapToOppfolgingsplanMetadata
import no.nav.syfo.oppfolgingsplan.dto.mapToUtkastMetadata
import no.nav.syfo.varsel.EsyfovarselProducer
import no.nav.syfo.varsel.domain.ArbeidstakerHendelse
import no.nav.syfo.varsel.domain.HendelseType

class OppfolgingsplanService(
    private val database: DatabaseInterface,
    private val esyfovarselProducer: EsyfovarselProducer,
) {

    fun persistOppfolgingsplan(
        narmesteLederId: String,
        createOppfolgingsplanRequest: CreateOppfolgingsplanRequest
    ): UUID {
        return database.persistOppfolgingsplanAndDeleteUtkast(narmesteLederId, createOppfolgingsplanRequest)
    }

    fun persistOppfolgingsplanUtkast(narmesteLederId: String, utkast: CreateUtkastRequest) {
        database.upsertOppfolgingsplanUtkast(narmesteLederId, utkast)
    }

    fun getOppfolgingsplanUtkast(sykmeldtFnr: String, orgnummer: String): PersistedOppfolgingsplanUtkast? {
        return database.findOppfolgingsplanUtkastBy(sykmeldtFnr, orgnummer)
    }

    fun getOppfolgingsplanByUuid(uuid: UUID): PersistedOppfolgingsplan? {
        return database.findOppfolgingsplanBy(uuid)
    }

    fun getOppfolginsplanOverviewFor(sykmeldtFnr: String, orgnummer: String): OppfolgingsplanOverview {
        val utkast = database.findOppfolgingsplanUtkastBy(sykmeldtFnr, orgnummer)
            ?.mapToUtkastMetadata()
        val oppfolgingsplaner = database.findAllOppfolgingsplanerBy(sykmeldtFnr, orgnummer)
            .map { it.mapToOppfolgingsplanMetadata() }

        return OppfolgingsplanOverview(
            utkast = utkast,
            oppfolgingsplan = oppfolgingsplaner.firstOrNull(),
            previousOppfolgingsplaner = oppfolgingsplaner.drop(1),
        )
    }

    fun getOppfolginsplanOverviewFor(sykmeldtFnr: String): SykmeldtOppfolgingsplanOverview {
        val oppfolgingsplaner = database.findAllOppfolgingsplanerBy(sykmeldtFnr)
            .map { it.mapToOppfolgingsplanMetadata() }
        val (current, previous) = oppfolgingsplaner.partition { it.sluttdato >= LocalDate.now() }
        return SykmeldtOppfolgingsplanOverview(
            oppfolgingsplaner = current,
            previousOppfolgingsplaner = previous,
        )
    }

    fun produceVarsel(oppfolgingsplan: CreateOppfolgingsplanRequest) {
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
