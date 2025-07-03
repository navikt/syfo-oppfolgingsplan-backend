package no.nav.syfo.oppfolgingsplan.service

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
import no.nav.syfo.oppfolgingsplan.dto.OppfolgingsplanMetadata
import no.nav.syfo.oppfolgingsplan.dto.UtkastMetadata
import java.util.UUID

class OppfolgingsplanService(
    private val database: DatabaseInterface,
) {

    fun persistOppfolgingsplan(narmesteLederId: String, createOppfolgingsplanRequest: CreateOppfolgingsplanRequest) {
        database.persistOppfolgingsplanAndDeleteUtkast(narmesteLederId, createOppfolgingsplanRequest)
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
}

fun PersistedOppfolgingsplanUtkast.mapToUtkastMetadata(): UtkastMetadata {
    return UtkastMetadata(
        uuid = uuid,
        sykmeldtFnr = sykmeldtFnr,
        narmesteLederFnr = narmesteLederFnr,
        orgnummer = orgnummer,
        sluttdato = sluttdato,
    )
}

fun PersistedOppfolgingsplan.mapToOppfolgingsplanMetadata(): OppfolgingsplanMetadata {
    return OppfolgingsplanMetadata(
        uuid = uuid,
        sykmeldtFnr = sykmeldtFnr,
        narmesteLederFnr = narmesteLederFnr,
        orgnummer = orgnummer,
        sluttdato = sluttdato,
        skalDelesMedLege = skalDelesMedLege,
        skalDelesMedVeileder = skalDelesMedVeileder,
        deltMedLegeTidspunkt = deltMedLegeTidspunkt,
        deltMedVeilederTidspunkt = deltMedVeilederTidspunkt,
        createdAt = createdAt,
    )
}