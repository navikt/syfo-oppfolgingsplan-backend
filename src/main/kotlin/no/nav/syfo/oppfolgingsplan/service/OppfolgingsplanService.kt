package no.nav.syfo.oppfolgingsplan.service

import io.ktor.server.plugins.BadRequestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.exception.PlanNotFoundException
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.dinesykmeldte.client.getOrganizationName
import no.nav.syfo.oppfolgingsplan.api.v1.veileder.OppfolgingsplanVeileder
import no.nav.syfo.oppfolgingsplan.db.deleteOppfolgingsplanUtkast
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedOppfolgingsplanUtkast
import no.nav.syfo.oppfolgingsplan.db.domain.toOppfolgingsplanMetadata
import no.nav.syfo.oppfolgingsplan.db.domain.toUtkastMetadata
import no.nav.syfo.oppfolgingsplan.db.findAllOppfolgingsplanerBy
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanBy
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanUtkastBy
import no.nav.syfo.oppfolgingsplan.db.persistOppfolgingsplanAndDeleteUtkast
import no.nav.syfo.oppfolgingsplan.db.setDeltMedLegeTidspunkt
import no.nav.syfo.oppfolgingsplan.db.setDeltMedVeilederTidspunkt
import no.nav.syfo.oppfolgingsplan.db.setJournalpostId
import no.nav.syfo.oppfolgingsplan.db.setNarmesteLederFullName
import no.nav.syfo.oppfolgingsplan.db.updateDelingAvPlanMedVeileder
import no.nav.syfo.oppfolgingsplan.db.updateSkalDelesMedLege
import no.nav.syfo.oppfolgingsplan.db.updateSkalDelesMedVeileder
import no.nav.syfo.oppfolgingsplan.db.upsertOppfolgingsplanUtkast
import no.nav.syfo.oppfolgingsplan.domain.EmployeeDetails
import no.nav.syfo.oppfolgingsplan.domain.OrganizationDetails
import no.nav.syfo.oppfolgingsplan.dto.ArbeidsgiverOppfolgingsplanOverviewResponse
import no.nav.syfo.oppfolgingsplan.dto.CreateOppfolgingsplanRequest
import no.nav.syfo.oppfolgingsplan.dto.LagreUtkastRequest
import no.nav.syfo.oppfolgingsplan.dto.LagreUtkastResponse
import no.nav.syfo.oppfolgingsplan.dto.OversiktResponseData
import no.nav.syfo.pdl.PdlService
import no.nav.syfo.util.logger
import no.nav.syfo.varsel.EsyfovarselProducer
import no.nav.syfo.varsel.domain.ArbeidstakerHendelse
import no.nav.syfo.varsel.domain.HendelseType
import java.time.Instant
import java.util.*

/**
 * Service for managing oppfølgingsplaner.
 *
 * All database operations are wrapped in withContext(Dispatchers.IO) to avoid blocking
 * Ktor's request handling threads. This is important to maintain good throughput and low latency under load.
 */
class OppfolgingsplanService(
    private val database: DatabaseInterface,
    private val esyfovarselProducer: EsyfovarselProducer,
    private val pdlService: PdlService,
) {
    private val logger = logger()

    suspend fun createOppfolgingsplan(
        narmesteLederFnr: String,
        sykmeldt: Sykmeldt,
        createOppfolgingsplanRequest: CreateOppfolgingsplanRequest
    ): UUID {
        val uuid = withContext(Dispatchers.IO) {
            database.persistOppfolgingsplanAndDeleteUtkast(narmesteLederFnr, sykmeldt, createOppfolgingsplanRequest)
        }

        try {
            withContext(Dispatchers.IO) {
                produceOppfolgingsplanCreatedVarsel(sykmeldt)
            }
        } catch (e: Exception) {
            logger.error("Error when producing kafka message", e)
        }

        return uuid
    }

    suspend fun persistOppfolgingsplanUtkast(
        narmesteLederFnr: String,
        sykmeldt: Sykmeldt,
        utkast: LagreUtkastRequest
    ): LagreUtkastResponse {
        val updatedAt = withContext(Dispatchers.IO) {
            val (_, updatedAt) = database.upsertOppfolgingsplanUtkast(
                narmesteLederFnr,
                sykmeldt,
                utkast
            )
            updatedAt
        }

        return LagreUtkastResponse(
            sistLagretTidspunkt = updatedAt
        )
    }

    suspend fun deleteOppfolgingsplanUtkast(sykmeldt: Sykmeldt) {
        if (sykmeldt.aktivSykmelding != true) {
            throw BadRequestException(
                "Cannot delete oppfolgingsplan utkast for sykmeldt without active sykmelding"
            )
        }

        withContext(Dispatchers.IO) {
            database.deleteOppfolgingsplanUtkast(sykmeldt)
        }
    }

    suspend fun getPersistedOppfolgingsplanUtkast(sykmeldt: Sykmeldt): PersistedOppfolgingsplanUtkast? {
        return withContext(Dispatchers.IO) {
            database.findOppfolgingsplanUtkastBy(
                sykmeldtFnr = sykmeldt.fnr,
                organisasjonsnummer = sykmeldt.orgnummer
            )
        }
    }

    suspend fun getPersistedOppfolgingsplanByUuid(uuid: UUID): PersistedOppfolgingsplan {
        return withContext(Dispatchers.IO) {
            database.findOppfolgingsplanBy(uuid)
        } ?: throw PlanNotFoundException("Oppfolgingsplan not found for uuid: $uuid")
    }

    suspend fun updateSkalDelesMedLege(
        uuid: UUID,
        skalDelesMedLege: Boolean
    ) {
        withContext(Dispatchers.IO) {
            database.updateSkalDelesMedLege(uuid, skalDelesMedLege)
        }
    }

    suspend fun updateSkalDelesMedVeileder(
        uuid: UUID,
        skalDelesMedVeileder: Boolean
    ) {
        withContext(Dispatchers.IO) {
            database.updateSkalDelesMedVeileder(uuid, skalDelesMedVeileder)
        }
    }

    suspend fun setDeltMedLegeTidspunkt(
        uuid: UUID,
        deltMedLegeTidspunkt: Instant
    ) {
        withContext(Dispatchers.IO) {
            database.setDeltMedLegeTidspunkt(uuid, deltMedLegeTidspunkt)
        }
    }

    suspend fun setDeltMedVeilederTidspunkt(
        uuid: UUID,
        deltMedVeilederTidspunkt: Instant
    ) {
        withContext(Dispatchers.IO) {
            database.setDeltMedVeilederTidspunkt(uuid, deltMedVeilederTidspunkt)
        }
    }

    suspend fun setJournalpostId(
        uuid: UUID,
        journalpostId: String,
    ) {
        withContext(Dispatchers.IO) {
            database.setJournalpostId(uuid, journalpostId)
        }
    }

    /**
     * Marks an oppfolgingsplan as shared with veileder and sets journalpost ID.
     * All updates are done in a single transaction to ensure consistency.
     */
    suspend fun updateDelingAvPlanMedVeileder(
        uuid: UUID,
        journalpostId: String,
    ): Instant {
        val deltMedVeilederTidspunkt = Instant.now()

        withContext(Dispatchers.IO) {
            database.updateDelingAvPlanMedVeileder(uuid, deltMedVeilederTidspunkt, journalpostId)
        }

        return deltMedVeilederTidspunkt;
    }

    suspend fun getAktivplanForSykmeldt(sykmeldt: Sykmeldt): PersistedOppfolgingsplan? {
        return withContext(Dispatchers.IO) {
            database.findAllOppfolgingsplanerBy(sykmeldt.fnr, sykmeldt.orgnummer)
        }.firstOrNull()
    }

    suspend fun getOppfolgingsplanOverviewFor(sykmeldt: Sykmeldt): ArbeidsgiverOppfolgingsplanOverviewResponse {
        val (utkast, oppfolgingsplaner) = withContext(Dispatchers.IO) {
            val utkast = database.findOppfolgingsplanUtkastBy(sykmeldt.fnr, sykmeldt.orgnummer)
                ?.toUtkastMetadata()
            val oppfolgingsplaner = database.findAllOppfolgingsplanerBy(sykmeldt.fnr, sykmeldt.orgnummer)
                .map { it.toOppfolgingsplanMetadata() }
            utkast to oppfolgingsplaner
        }

        return ArbeidsgiverOppfolgingsplanOverviewResponse(
            userHasEditAccess = sykmeldt.aktivSykmelding == true,
            organization = OrganizationDetails(
                orgNumber = sykmeldt.orgnummer,
                orgName = sykmeldt.getOrganizationName(),
            ),
            employee = EmployeeDetails(
                fnr = sykmeldt.fnr,
                name = sykmeldt.navn,
            ),
            oversikt = OversiktResponseData(
                utkast = utkast,
                aktivPlan = oppfolgingsplaner.firstOrNull(),
                tidligerePlaner = oppfolgingsplaner.drop(1),
            )
        )
    }

    suspend fun getPersistedOppfolgingsplanListBy(sykmeldtFnr: String): List<PersistedOppfolgingsplan> =
        withContext(Dispatchers.IO) {
            database.findAllOppfolgingsplanerBy(sykmeldtFnr)
        }

    suspend fun getAndSetNarmestelederFullname(
        persistedOppfolgingsplan: PersistedOppfolgingsplan
    ): PersistedOppfolgingsplan {
        return if (persistedOppfolgingsplan.narmesteLederFullName.isNullOrEmpty()) {
            pdlService.getNameFor(
                persistedOppfolgingsplan.narmesteLederFnr
            )?.let { narmesteLederName ->
                withContext(Dispatchers.IO) {
                    database.setNarmesteLederFullName(
                        persistedOppfolgingsplan.uuid,
                        narmesteLederName
                    )
                }
                persistedOppfolgingsplan.copy(narmesteLederFullName = narmesteLederName)
            } ?: persistedOppfolgingsplan
        } else persistedOppfolgingsplan
    }

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

fun List<PersistedOppfolgingsplan>.toListOppfolgingsplanVeileder(): List<OppfolgingsplanVeileder> =
    this.filter { it.deltMedVeilederTidspunkt != null }
        .sortedByDescending { it.createdAt }
        .map {
            OppfolgingsplanVeileder.from(it)
        }
