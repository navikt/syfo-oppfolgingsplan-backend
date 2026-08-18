package no.nav.syfo.oppfolgingsplan.service

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.exception.ApiErrorException
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.dinesykmeldte.client.getOrganizationName
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedUnntaksvurdering
import no.nav.syfo.oppfolgingsplan.db.domain.toUnntaksvurderingMetadata
import no.nav.syfo.oppfolgingsplan.db.existsAktivPlanOrUtkast
import no.nav.syfo.oppfolgingsplan.db.findAllUnntaksvurderingerBy
import no.nav.syfo.oppfolgingsplan.db.persistUnntaksvurdering
import no.nav.syfo.oppfolgingsplan.db.setUnntaksvurderingNarmesteLederFullName
import no.nav.syfo.oppfolgingsplan.db.softDeleteExpiredUnntaksvurderinger
import no.nav.syfo.oppfolgingsplan.dto.UnntaksvurderingMetadata
import no.nav.syfo.pdl.PdlService
import java.util.UUID

class UnntaksvurderingService(
    private val database: DatabaseInterface,
    private val pdlService: PdlService,
) {
    suspend fun createUnntaksvurdering(
        narmesteLederFnr: String,
        sykmeldt: Sykmeldt,
    ): UUID {
        val existing = database.existsAktivPlanOrUtkast(sykmeldt.fnr, sykmeldt.orgnummer)

        if (existing.aktivPlanExists) {
            throw ApiErrorException.Conflict("Cannot create unntaksvurdering when an aktiv oppfolgingsplan exists")
        }
        if (existing.utkastExists) {
            throw ApiErrorException.Conflict("Cannot create unntaksvurdering when an oppfolgingsplan utkast exists")
        }

        val narmesteLederFullName = pdlService.getNameFor(narmesteLederFnr)

        return database.persistUnntaksvurdering(narmesteLederFnr, sykmeldt, narmesteLederFullName)
    }

    suspend fun getUnntaksvurderingerFor(
        sykmeldt: Sykmeldt,
    ): List<UnntaksvurderingMetadata> = database
        .findAllUnntaksvurderingerBy(sykmeldt.fnr, sykmeldt.orgnummer)
        .tilMetadataMedNavn(organisasjonsnavnFallback = sykmeldt.getOrganizationName())

    suspend fun getUnntaksvurderingerForSykmeldt(
        sykmeldtFnr: String,
    ): List<UnntaksvurderingMetadata> = database
        .findAllUnntaksvurderingerBy(sykmeldtFnr)
        .tilMetadataMedNavn()

    suspend fun softDeleteExpiredUnntaksvurderinger(): Int = runSoftDeleteBatchLoop {
        database.softDeleteExpiredUnntaksvurderinger()
    }

    private suspend fun List<PersistedUnntaksvurdering>.tilMetadataMedNavn(
        organisasjonsnavnFallback: String? = null,
    ): List<UnntaksvurderingMetadata> = backfillNarmesteLederFullName()
        .map { it.toUnntaksvurderingMetadata(organisasjonsnavnFallback) }

    /**
     * Ledernavnet persisteres ved opprettelse, men kolonnen er nullable for eldre rader. Manglende
     * navn slås opp i PDL (ett oppslag per unikt fnr) og skrives tilbake, slik at oppslaget bare
     * skjer én gang per rad.
     */
    private suspend fun List<PersistedUnntaksvurdering>.backfillNarmesteLederFullName(): List<PersistedUnntaksvurdering> {
        val navnPerFnr = filter { it.narmesteLederFullName.isNullOrEmpty() }
            .map { it.narmesteLederFnr }
            .distinct()
            .mapNotNull { fnr -> pdlService.getNameFor(fnr)?.let { navn -> fnr to navn } }
            .toMap()

        if (navnPerFnr.isEmpty()) return this

        return map { unntaksvurdering ->
            val navn = navnPerFnr[unntaksvurdering.narmesteLederFnr]
            if (unntaksvurdering.narmesteLederFullName.isNullOrEmpty() && navn != null) {
                database.setUnntaksvurderingNarmesteLederFullName(unntaksvurdering.uuid, navn)
                unntaksvurdering.copy(narmesteLederFullName = navn)
            } else {
                unntaksvurdering
            }
        }
    }
}
