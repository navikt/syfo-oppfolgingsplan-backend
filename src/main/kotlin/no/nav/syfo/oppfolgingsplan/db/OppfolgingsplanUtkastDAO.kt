package no.nav.syfo.oppfolgingsplan.db

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedOppfolgingsplanUtkast
import no.nav.syfo.oppfolgingsplan.dto.LagreUtkastRequest
import no.nav.syfo.util.configuredJacksonMapper
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.UUID

private val objectMapper = configuredJacksonMapper

fun DatabaseInterface.upsertOppfolgingsplanUtkast(
    narmesteLederFnr: String,
    sykmeldt: Sykmeldt,
    lagreUtkastRequest: LagreUtkastRequest,
): Pair<UUID, Instant> {
    val statement =
        """
        INSERT INTO oppfolgingsplan_utkast (
            sykmeldt_fnr,
            narmeste_leder_id,
            narmeste_leder_fnr,
            organisasjonsnummer,
            content,
            created_at,
            updated_at
        ) VALUES (?, ?, ?, ?, ?, NOW(), NOW())
        ON CONFLICT (narmeste_leder_id) DO UPDATE SET
            sykmeldt_fnr = EXCLUDED.sykmeldt_fnr,
            narmeste_leder_fnr = EXCLUDED.narmeste_leder_fnr,
            organisasjonsnummer = EXCLUDED.organisasjonsnummer,
            content = EXCLUDED.content,
            updated_at = NOW()
        RETURNING uuid, updated_at
        """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(statement).use { preparedStatement ->
            preparedStatement.setString(1, sykmeldt.fnr)
            preparedStatement.setString(2, sykmeldt.narmestelederId)
            preparedStatement.setString(3, narmesteLederFnr)
            preparedStatement.setString(4, sykmeldt.orgnummer)
            preparedStatement.setObject(
                5,
                objectMapper.writeValueAsString(lagreUtkastRequest.content),
                Types.OTHER,
            )

            val resultSet = preparedStatement.executeQuery()
            connection.commit()
            resultSet.next()

            val uuid = resultSet.getObject("uuid", UUID::class.java)
            val updatedAt = resultSet.getTimestamp("updated_at").toInstant()

            return uuid to updatedAt
        }
    }
}

fun DatabaseInterface.deleteOppfolgingsplanUtkast(
    sykmeldt: Sykmeldt,
) {
    val statement =
        """
        DELETE FROM oppfolgingsplan_utkast
        WHERE sykmeldt_fnr = ?
        AND organisasjonsnummer = ?
        """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(statement).use { preparedStatement ->
            preparedStatement.setString(1, sykmeldt.fnr)
            preparedStatement.setString(2, sykmeldt.orgnummer)
            preparedStatement.executeUpdate()
            connection.commit()
        }
    }
}

fun DatabaseInterface.deleteExpiredOppfolgingsplanUtkast(
    retentionMonths: Int,
    limit: Int,
): Int = executeDeleteExpiredOppfolgingsplanUtkast(
    statement = """
        DELETE FROM oppfolgingsplan_utkast
        WHERE uuid IN (
            SELECT uuid FROM oppfolgingsplan_utkast
            WHERE updated_at < NOW() - make_interval(months => ?)
            ORDER BY updated_at
            LIMIT ?
        )
    """.trimIndent(),
) { preparedStatement ->
    preparedStatement.setInt(1, retentionMonths)
    preparedStatement.setInt(2, limit)
}

fun DatabaseInterface.deleteExpiredOppfolgingsplanUtkastUpdatedBefore(
    updatedBefore: Instant,
    limit: Int,
): Int = executeDeleteExpiredOppfolgingsplanUtkast(
    statement = """
        DELETE FROM oppfolgingsplan_utkast
        WHERE uuid IN (
            SELECT uuid FROM oppfolgingsplan_utkast
            WHERE updated_at < ?
            ORDER BY updated_at
            LIMIT ?
        )
    """.trimIndent(),
) { preparedStatement ->
    preparedStatement.setTimestamp(1, Timestamp.from(updatedBefore))
    preparedStatement.setInt(2, limit)
}

fun DatabaseInterface.findOppfolgingsplanUtkastBy(
    sykmeldtFnr: String,
    organisasjonsnummer: String,
): PersistedOppfolgingsplanUtkast? {
    val statement =
        """
        SELECT *
        FROM oppfolgingsplan_utkast
        WHERE sykmeldt_fnr = ?
        AND organisasjonsnummer = ?
        """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(statement).use { preparedStatement ->
            preparedStatement.setString(1, sykmeldtFnr)
            preparedStatement.setString(2, organisasjonsnummer)
            val resultSet = preparedStatement.executeQuery()
            return if (resultSet.next()) {
                resultSet.toOppfolgingsplanUtkastDTO()
            } else {
                null
            }
        }
    }
}

private fun DatabaseInterface.executeDeleteExpiredOppfolgingsplanUtkast(
    statement: String,
    setParameters: (java.sql.PreparedStatement) -> Unit,
): Int {
    connection.use { connection ->
        connection.prepareStatement(statement).use { preparedStatement ->
            setParameters(preparedStatement)
            val deletedRows = preparedStatement.executeUpdate()
            connection.commit()
            return deletedRows
        }
    }
}

fun ResultSet.toOppfolgingsplanUtkastDTO(): PersistedOppfolgingsplanUtkast = PersistedOppfolgingsplanUtkast(
    uuid = getObject("uuid") as UUID,
    sykmeldtFnr = getString("sykmeldt_fnr"),
    narmesteLederId = getString("narmeste_leder_id"),
    narmesteLederFnr = getString("narmeste_leder_fnr"),
    organisasjonsnummer = getString("organisasjonsnummer"),
    content = objectMapper.readValue(getString("content")),
    createdAt = getTimestamp("created_at").toInstant(),
    updatedAt = getTimestamp("updated_at").toInstant(),
)
