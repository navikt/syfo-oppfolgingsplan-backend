package no.nav.syfo.oppfolgingsplan.db

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedOppfolgingsplanUtkast
import no.nav.syfo.oppfolgingsplan.dto.CreateUtkastRequest
import no.nav.syfo.util.configuredJacksonMapper
import java.sql.ResultSet
import java.sql.Types
import java.util.*

private val objectMapper = configuredJacksonMapper

fun DatabaseInterface.upsertOppfolgingsplanUtkast(
    narmesteLederFnr: String,
    sykmeldt: Sykmeldt,
    createUtkastRequest: CreateUtkastRequest,
): UUID {
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
        RETURNING uuid
        """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(statement).use { preparedStatement ->
            preparedStatement.setString(1, sykmeldt.fnr)
            preparedStatement.setString(2, sykmeldt.narmestelederId)
            preparedStatement.setString(3, narmesteLederFnr)
            preparedStatement.setString(4, sykmeldt.orgnummer)
            preparedStatement.setObject(5, objectMapper.writeValueAsString(createUtkastRequest.content), Types.OTHER)
            val resultSet = preparedStatement.executeQuery()
            connection.commit()
            resultSet.next()
            return resultSet.getObject("uuid", UUID::class.java)
        }
    }
}

fun DatabaseInterface.deleteOppfolgingsplanUtkast(
    sykmeldt: Sykmeldt
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

fun DatabaseInterface.findOppfolgingsplanUtkastBy(
    sykmeldtFnr: String,
    organisasjonsnummer: String
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

fun ResultSet.toOppfolgingsplanUtkastDTO(): PersistedOppfolgingsplanUtkast {
    return PersistedOppfolgingsplanUtkast(
        uuid = getObject("uuid") as UUID,
        sykmeldtFnr = getString("sykmeldt_fnr"),
        narmesteLederId = getString("narmeste_leder_id"),
        narmesteLederFnr = getString("narmeste_leder_fnr"),
        organisasjonsnummer = getString("organisasjonsnummer"),
        content = objectMapper.readValue(getString("content")),
        createdAt = getTimestamp("created_at").toInstant(),
        updatedAt = getTimestamp("updated_at").toInstant()
    )
}
