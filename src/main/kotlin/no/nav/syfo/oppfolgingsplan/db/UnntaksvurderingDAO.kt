package no.nav.syfo.oppfolgingsplan.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedUnntaksvurdering
import java.sql.ResultSet
import java.util.UUID

fun DatabaseInterface.persistUnntaksvurdering(
    narmesteLederFnr: String,
    sykmeldt: Sykmeldt,
    narmesteLederFullName: String?,
): UUID {
    val statement = """
        INSERT INTO unntaksvurdering (
            sykmeldt_fnr,
            organisasjonsnummer,
            narmeste_leder_fnr,
            narmeste_leder_full_name
        ) VALUES (?, ?, ?, ?)
        RETURNING uuid
    """.trimIndent()

    connection.use { connection ->
        val uuid = connection.prepareStatement(statement).use {
            it.setString(1, sykmeldt.fnr)
            it.setString(2, sykmeldt.orgnummer)
            it.setString(3, narmesteLederFnr)
            it.setString(4, narmesteLederFullName)
            val resultSet = it.executeQuery()
            resultSet.next()
            resultSet.getObject("uuid", UUID::class.java)
        }
        connection.commit()
        return uuid
    }
}

fun DatabaseInterface.findAllUnntaksvurderingerBy(
    sykmeldtFnr: String,
    organisasjonsnummer: String,
): List<PersistedUnntaksvurdering> {
    val statement = """
        SELECT *
        FROM unntaksvurdering
        WHERE sykmeldt_fnr = ?
        AND organisasjonsnummer = ?
        AND skjult_fra IS NULL
        ORDER BY created_at DESC
    """.trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(statement).use { preparedStatement ->
            preparedStatement.setString(1, sykmeldtFnr)
            preparedStatement.setString(2, organisasjonsnummer)
            preparedStatement.executeQuery().use { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(resultSet.mapToUnntaksvurdering())
                    }
                }
            }
        }
    }
}

fun ResultSet.mapToUnntaksvurdering(): PersistedUnntaksvurdering = PersistedUnntaksvurdering(
    uuid = getObject("uuid") as UUID,
    sykmeldtFnr = getString("sykmeldt_fnr"),
    organisasjonsnummer = getString("organisasjonsnummer"),
    narmesteLederFnr = getString("narmeste_leder_fnr"),
    narmesteLederFullName = getString("narmeste_leder_full_name"),
    createdAt = getTimestamp("created_at").toInstant(),
    skjultFra = getTimestamp("skjult_fra")?.toInstant(),
)
