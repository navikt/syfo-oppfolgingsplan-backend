package no.nav.syfo.oppfolgingsplan.service

import java.time.LocalDate
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.oppfolgingsplan.db.PersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.db.PersistedOppfolgingsplanUtkast
import no.nav.syfo.oppfolgingsplan.db.findAllOppfolgingsplanerBy
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanBy
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanUtkastBy
import no.nav.syfo.oppfolgingsplan.db.persistOppfolgingsplanAndDeleteUtkast
import no.nav.syfo.oppfolgingsplan.db.setDeltMedLegeTidspunkt
import no.nav.syfo.oppfolgingsplan.db.updateSkalDelesMedLege
import no.nav.syfo.oppfolgingsplan.db.upsertOppfolgingsplanUtkast
import no.nav.syfo.oppfolgingsplan.dto.CreateOppfolgingsplanRequest
import no.nav.syfo.oppfolgingsplan.dto.OppfolgingsplanOverview
import no.nav.syfo.oppfolgingsplan.dto.CreateUtkastRequest
import java.util.UUID
import no.nav.syfo.oppfolgingsplan.dto.SykmeldtOppfolgingsplanOverview
import no.nav.syfo.oppfolgingsplan.dto.mapToOppfolgingsplanMetadata
import no.nav.syfo.oppfolgingsplan.dto.mapToUtkastMetadata
import no.nav.syfo.util.logger
import no.nav.syfo.varsel.EsyfovarselProducer
import no.nav.syfo.varsel.domain.ArbeidstakerHendelse
import no.nav.syfo.varsel.domain.HendelseType
import java.time.Instant
import java.time.ZoneId
import no.nav.syfo.oppfolgingsplan.api.v1.veilder.OppfolgingsplanVeilder
import no.nav.syfo.oppfolgingsplan.db.setDeltMedVeilderTidspunkt
import no.nav.syfo.oppfolgingsplan.db.updateSkalDelesMedVeileder
import no.nav.syfo.oppfolgingsplan.dto.OppfolgingsplanMetadata

class OppfolgingsplanService(
    private val database: DatabaseInterface,
    private val esyfovarselProducer: EsyfovarselProducer,
) {
    private val logger = logger()

    fun createOppfolgingsplan(
        narmesteLederFnr: String,
        sykmeldt: Sykmeldt,
        createOppfolgingsplanRequest: CreateOppfolgingsplanRequest
    ): UUID {
        val uuid =
            database.persistOppfolgingsplanAndDeleteUtkast(narmesteLederFnr, sykmeldt, createOppfolgingsplanRequest)

        try {
            produceOppfolgingsplanCreatedVarsel(sykmeldt)
        } catch (e: Exception) {
            logger.error("Error when producing kafka message", e)
        }

        return uuid
    }

    fun persistOppfolgingsplanUtkast(narmesteLederFnr: String, sykmeldt: Sykmeldt, utkast: CreateUtkastRequest) {
        database.upsertOppfolgingsplanUtkast(
            narmesteLederFnr,
            sykmeldt,
            utkast
        )
    }

    fun getOppfolgingsplanUtkast(sykmeldtFnr: String, orgnummer: String): PersistedOppfolgingsplanUtkast? {
        return database.findOppfolgingsplanUtkastBy(sykmeldtFnr, orgnummer)
    }

    fun getOppfolgingsplanByUuid(uuid: UUID): PersistedOppfolgingsplan? {
        return database.findOppfolgingsplanBy(uuid)
    }

    fun updateSkalDelesMedLege(
        uuid: UUID,
        skalDelesMedLege: Boolean
    ) {
        database.updateSkalDelesMedLege(uuid, skalDelesMedLege)
    }

    fun updateSkalDelesMedVeileder(
        uuid: UUID,
        skalDelesMedVeilder: Boolean
    ) {
        database.updateSkalDelesMedVeileder(uuid, skalDelesMedVeilder)
    }

    fun setDeltMedLegeTidspunkt(
        uuid: UUID,
        deltMedLegeTidspunkt: Instant
    ) {
        database.setDeltMedLegeTidspunkt(uuid, deltMedLegeTidspunkt)
    }

    fun setDeltMedVeilederTidspunkt(
        uuid: UUID,
        deltMedVeilederTidspunkt: Instant
    ) {
        database.setDeltMedVeilderTidspunkt(uuid, deltMedVeilederTidspunkt)
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

    fun getOppfolginsplanOverviewFor(sykmeldtFnr: String): List<OppfolgingsplanMetadata> =
        database.findAllOppfolgingsplanerBy(sykmeldtFnr)
            .map { it.mapToOppfolgingsplanMetadata() }


    private fun produceOppfolgingsplanCreatedVarsel(sykmeldt: Sykmeldt) {
        val hendelse = ArbeidstakerHendelse(
            type = HendelseType.SM_OPPFOLGINGSPLAN_OPPRETTET,
            ferdigstill = false,
            arbeidstakerFnr = sykmeldt.fnr,
            data = null,
            orgnummer = sykmeldt.orgnummer,
        )
        esyfovarselProducer.sendVarselToEsyfovarsel(hendelse)
    }
}

fun List<OppfolgingsplanMetadata>.toSykmeldtOppfolgingsplanOverview(): SykmeldtOppfolgingsplanOverview {
    val (current, previous) = this.partition { it.evalueringsdato >= LocalDate.now() }
    return SykmeldtOppfolgingsplanOverview(
        oppfolgingsplaner = current,
        previousOppfolgingsplaner = previous,
    )
}

fun List<OppfolgingsplanMetadata>.toListOppfolginsplanVeiler(): List<OppfolgingsplanVeilder> =
    this.filter { it.deltMedVeilederTidspunkt != null }
        .sortedByDescending { it.createdAt }
        .map {
            require(it.deltMedVeilederTidspunkt != null) {
                "Oppfolgingsplan ${it.uuid} has null deltMedVeilederTidspunkt"
            }
            OppfolgingsplanVeilder(
                uuid = it.uuid,
                fnr = it.sykmeldtFnr,
                virksomhetsnummer = it.organisasjonsnummer,
                opprettet = it.createdAt.atZone(ZoneId.systemDefault()).toLocalDateTime(),
                deltMedNavTidspunkt = it.deltMedVeilederTidspunkt.atZone(ZoneId.systemDefault()).toLocalDateTime()
            )
        }
