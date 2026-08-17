package no.nav.syfo.oppfolgingsplan.service

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.exception.ApiErrorException
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
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
        .map { getAndSetNarmesteLederFullName(it) }
        .map { it.toUnntaksvurderingMetadata() }

    suspend fun getUnntaksvurderingerForSykmeldt(
        sykmeldtFnr: String,
    ): List<UnntaksvurderingMetadata> = database
        .findAllUnntaksvurderingerBy(sykmeldtFnr)
        .map { getAndSetNarmesteLederFullName(it) }
        .map { it.toUnntaksvurderingMetadata() }

    suspend fun softDeleteExpiredUnntaksvurderinger(): Int = runSoftDeleteBatchLoop {
        database.softDeleteExpiredUnntaksvurderinger()
    }

    private suspend fun getAndSetNarmesteLederFullName(
        unntaksvurdering: PersistedUnntaksvurdering,
    ): PersistedUnntaksvurdering = if (unntaksvurdering.narmesteLederFullName.isNullOrEmpty()) {
        pdlService.getNameFor(unntaksvurdering.narmesteLederFnr)?.let { narmesteLederName ->
            database.setUnntaksvurderingNarmesteLederFullName(unntaksvurdering.uuid, narmesteLederName)
            unntaksvurdering.copy(narmesteLederFullName = narmesteLederName)
        } ?: unntaksvurdering
    } else {
        unntaksvurdering
    }
}
