package no.nav.syfo.oppfolgingsplan.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.oppfolgingsplan.dto.CreateUtkastRequest
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.FormSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.jsonToFormSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.toJsonString
import java.sql.Date
import java.sql.ResultSet
import java.sql.Types
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class PersistedOppfolgingsplanUtkast (
    val uuid: UUID,
    val sykmeldtFnr: String,
    val narmesteLederId: String,
    val narmesteLederFnr: String,
    val organisasjonsnummer: String,
    val content: FormSnapshot?,
    val sluttdato: LocalDate?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

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
            sluttdato,
            created_at,
            updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
        ON CONFLICT (narmeste_leder_id) DO UPDATE SET
            sykmeldt_fnr = EXCLUDED.sykmeldt_fnr,
            narmeste_leder_fnr = EXCLUDED.narmeste_leder_fnr,
            organisasjonsnummer = EXCLUDED.organisasjonsnummer,
            content = EXCLUDED.content,
            sluttdato = EXCLUDED.sluttdato,
            updated_at = NOW()
        RETURNING uuid
        """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(statement).use { preparedStatement ->
            preparedStatement.setString(1, sykmeldt.fnr)
            preparedStatement.setString(2, sykmeldt.narmestelederId)
            preparedStatement.setString(3, narmesteLederFnr)
            preparedStatement.setString(4, sykmeldt.orgnummer)
            preparedStatement.setObject(5, createUtkastRequest.content?.toJsonString(), Types.OTHER)
            preparedStatement.setDate(6, Date.valueOf(createUtkastRequest.sluttdato.toString()))
            val resultSet = preparedStatement.executeQuery()
            connection.commit()
            resultSet.next()
            return resultSet.getObject("uuid", UUID::class.java)
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
        content = FormSnapshot.jsonToFormSnapshot(getString("content")),
        sluttdato = getDate("sluttdato")?.toLocalDate(),
        createdAt = getTimestamp("created_at").toInstant(),
        updatedAt = getTimestamp("updated_at").toInstant()
    )
}
