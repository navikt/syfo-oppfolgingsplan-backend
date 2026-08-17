package no.nav.syfo.oppfolgingsplan.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.exposedTransaction
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.dinesykmeldte.client.getOrganizationName
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedUnntaksvurdering
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.sql.Connection
import java.util.UUID

suspend fun DatabaseInterface.persistUnntaksvurdering(
    narmesteLederFnr: String,
    sykmeldt: Sykmeldt,
    narmesteLederFullName: String?,
): UUID = exposedTransaction {
    UnntaksvurderingTable.insertReturning(listOf(UnntaksvurderingTable.uuid)) {
        it[UnntaksvurderingTable.sykmeldtFnr] = sykmeldt.fnr
        it[UnntaksvurderingTable.organisasjonsnummer] = sykmeldt.orgnummer
        it[UnntaksvurderingTable.organisasjonsnavn] = sykmeldt.getOrganizationName()
        it[UnntaksvurderingTable.narmesteLederFnr] = narmesteLederFnr
        it[UnntaksvurderingTable.narmesteLederFullName] = narmesteLederFullName
    }.single()[UnntaksvurderingTable.uuid]
}

suspend fun DatabaseInterface.findAllUnntaksvurderingerBy(
    sykmeldtFnr: String,
    organisasjonsnummer: String,
): List<PersistedUnntaksvurdering> = findAllVisibleUnntaksvurderingerBy(sykmeldtFnr, organisasjonsnummer)

suspend fun DatabaseInterface.findAllUnntaksvurderingerBy(
    sykmeldtFnr: String,
): List<PersistedUnntaksvurdering> = findAllVisibleUnntaksvurderingerBy(sykmeldtFnr)

private suspend fun DatabaseInterface.findAllVisibleUnntaksvurderingerBy(
    sykmeldtFnr: String,
    organisasjonsnummer: String? = null,
): List<PersistedUnntaksvurdering> = exposedTransaction(readOnly = true) {
    val condition = (UnntaksvurderingTable.sykmeldtFnr eq sykmeldtFnr) and
        UnntaksvurderingTable.skjultFra.isNull()
    val conditionForOrganization = organisasjonsnummer?.let {
        condition and (UnntaksvurderingTable.organisasjonsnummer eq it)
    } ?: condition

    UnntaksvurderingTable
        .selectAll()
        .where { conditionForOrganization }
        .orderBy(UnntaksvurderingTable.createdAt to SortOrder.DESC)
        .map { it.toPersistedUnntaksvurdering() }
}

suspend fun DatabaseInterface.softDeleteExpiredUnntaksvurderinger(
    batchSize: Int = 1000,
): Int = exposedTransaction {
    val statement = """
        WITH candidates AS (
            SELECT uv.uuid
            FROM unntaksvurdering uv
            JOIN LATERAL (
                SELECT MAX(sp.tom) AS latest_tom
                FROM sykmeldingsperiode sp
                WHERE sp.sykmeldt_fnr = uv.sykmeldt_fnr
                  AND sp.organisasjonsnummer = uv.organisasjonsnummer
                  AND sp.invalidated_at IS NULL
            ) latest_valid_sykmeldingsperiode ON true
            WHERE uv.skjult_fra IS NULL
              AND latest_valid_sykmeldingsperiode.latest_tom < CURRENT_DATE - CAST(? AS INTERVAL)
            ORDER BY uv.uuid
            LIMIT ?
        )
        UPDATE unntaksvurdering uv
        SET skjult_fra = NOW()
        FROM candidates
        WHERE uv.uuid = candidates.uuid
    """.trimIndent()

    (connection.connection as Connection).prepareStatement(statement).use {
        it.setString(1, SOFT_DELETE_RETENTION_INTERVAL)
        it.setInt(2, batchSize)
        it.executeUpdate()
    }
}

suspend fun DatabaseInterface.setUnntaksvurderingNarmesteLederFullName(
    uuid: UUID,
    narmesteLederFullName: String,
) {
    exposedTransaction {
        UnntaksvurderingTable.update({ UnntaksvurderingTable.uuid eq uuid }) {
            it[UnntaksvurderingTable.narmesteLederFullName] = narmesteLederFullName
        }
    }
}

private fun ResultRow.toPersistedUnntaksvurdering(): PersistedUnntaksvurdering = PersistedUnntaksvurdering(
    uuid = this[UnntaksvurderingTable.uuid],
    sykmeldtFnr = this[UnntaksvurderingTable.sykmeldtFnr],
    organisasjonsnummer = this[UnntaksvurderingTable.organisasjonsnummer],
    organisasjonsnavn = this[UnntaksvurderingTable.organisasjonsnavn],
    narmesteLederFnr = this[UnntaksvurderingTable.narmesteLederFnr],
    narmesteLederFullName = this[UnntaksvurderingTable.narmesteLederFullName],
    createdAt = this[UnntaksvurderingTable.createdAt].toInstant(),
    skjultFra = this[UnntaksvurderingTable.skjultFra]?.toInstant(),
)
