package no.nav.syfo.oppfolgingsplan.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedPaaminnelse
import java.sql.ResultSet
import java.util.UUID

fun DatabaseInterface.upsertPaaminnelse(
    sykmeldt: Sykmeldt,
    bestilt: Boolean,
    sykmeldingsperiodeId: UUID,
): PersistedPaaminnelse {
    val statement =
        """
        INSERT INTO paaminnelse (
            organisasjonsnummer,
            sykmeldt_fnr,
            bestilt,
            created_at,
            updated_at,
            sykmeldingsperiode_id
        ) VALUES (?, ?, ?, NOW(), NOW(), ?)
        ON CONFLICT (sykmeldt_fnr, organisasjonsnummer) DO UPDATE SET
            bestilt = EXCLUDED.bestilt,
            sykmeldingsperiode_id = EXCLUDED.sykmeldingsperiode_id,
            updated_at = NOW()
        RETURNING *
        """.trimIndent()

    connection.use { connection ->
        var idx = 0
        connection.prepareStatement(statement).use { preparedStatement ->
            preparedStatement.setString(++idx, sykmeldt.orgnummer)
            preparedStatement.setString(++idx, sykmeldt.fnr)
            preparedStatement.setBoolean(++idx, bestilt)
            preparedStatement.setObject(++idx, sykmeldingsperiodeId)

            val resultSet = preparedStatement.executeQuery()
            connection.commit()
            resultSet.next()

            return resultSet.toPersistedPaaminnelse()
        }
    }
}

fun DatabaseInterface.findPaaminnelseBy(
    sykmeldtFnr: String,
    organisasjonsnummer: String,
): PersistedPaaminnelse? {
    val statement =
        """
        SELECT *
        FROM paaminnelse
        WHERE sykmeldt_fnr = ?
          AND organisasjonsnummer = ?
        """.trimIndent()

    connection.use { connection ->
        var idx = 0
        connection.prepareStatement(statement).use { preparedStatement ->
            preparedStatement.setString(++idx, sykmeldtFnr)
            preparedStatement.setString(++idx, organisasjonsnummer)
            val resultSet = preparedStatement.executeQuery()

            return if (resultSet.next()) {
                resultSet.toPersistedPaaminnelse()
            } else {
                null
            }
        }
    }
}

private fun ResultSet.toPersistedPaaminnelse(): PersistedPaaminnelse = PersistedPaaminnelse(
    organisasjonsnummer = getString("organisasjonsnummer"),
    sykmeldtFnr = getString("sykmeldt_fnr"),
    bestilt = getBoolean("bestilt"),
    sykmeldingsperiodeId = getObject("sykmeldingsperiode_id", UUID::class.java),
    createdAt = getTimestamp("created_at").toInstant(),
    updatedAt = getTimestamp("updated_at").toInstant(),
)
