package no.nav.syfo.oppfolgingsplan.db

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.oppfolgingsplan.dto.CreateOppfolgingsplanRequest
import java.sql.Date
import java.sql.ResultSet
import java.sql.Types
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class PersistedOppfolgingsplan(
    val uuid: UUID,
    val sykmeldtFnr: String,
    val narmesteLederId: String,
    val narmesteLederFnr: String,
    val orgnummer: String,
    val content: JsonNode,
    val sluttdato: LocalDate,
    val skalDelesMedLege: Boolean,
    val skalDelesMedVeileder: Boolean,
    val deltMedLegeTidspunkt: Instant? = null,
    val deltMedVeilederTidspunkt: Instant? = null,
    val createdAt: Instant
)

fun DatabaseInterface.persistOppfolgingsplanAndDeleteUtkast(
    narmesteLederId: String,
    createOppfolgingsplanRequest: CreateOppfolgingsplanRequest
) {
    val insertStatement = """
        INSERT INTO oppfolgingsplan (
            sykemeldt_fnr,
            narmeste_leder_id,
            narmeste_leder_fnr,
            orgnummer,
            content,
            sluttdato,
            skal_deles_med_lege,
            skal_deles_med_veileder,
            created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
    """.trimIndent()

    val deleteStatement = """
        DELETE FROM oppfolgingsplan_utkast
        WHERE narmeste_leder_id = ?
    """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(deleteStatement).use {
            it.setString(1, narmesteLederId)
            it.executeUpdate()
        }
        connection.prepareStatement(insertStatement).use {
            it.setString(1, createOppfolgingsplanRequest.sykmeldtFnr)
            it.setString(2, narmesteLederId)
            it.setString(3, createOppfolgingsplanRequest.narmesteLederFnr)
            it.setString(4, createOppfolgingsplanRequest.orgnummer)
            it.setObject(5, createOppfolgingsplanRequest.content.toString(), Types.OTHER)
            it.setDate(6, Date.valueOf(createOppfolgingsplanRequest.sluttdato.toString()))
            it.setBoolean(7, createOppfolgingsplanRequest.skalDelesMedLege)
            it.setBoolean(8, createOppfolgingsplanRequest.skalDelesMedVeileder)
            it.executeUpdate()
        }
        connection.commit()
    }
}

fun DatabaseInterface.findAllOppfolgingsplanerBy(
    sykmeldtFnr: String,
): List<PersistedOppfolgingsplan> {
    val statement = """
        SELECT *
        FROM oppfolgingsplan
        WHERE sykemeldt_fnr = ?
        ORDER BY created_at DESC
    """.trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(statement).use { preparedStatement ->
            preparedStatement.setString(1, sykmeldtFnr)
            preparedStatement.executeQuery().use { resultSet ->
                generateSequence { if (resultSet.next()) resultSet else null }
                    .map { it.mapToOppfolgingsplan() }
                    .toList()
            }
        }
    }
}

fun DatabaseInterface.findAllOppfolgingsplanerBy(
    sykmeldtFnr: String,
    orgnummer: String
): List<PersistedOppfolgingsplan> {
    val statement = """
        SELECT *
        FROM oppfolgingsplan
        WHERE sykemeldt_fnr = ?
        AND orgnummer = ?
        ORDER BY created_at DESC
    """.trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(statement).use { preparedStatement ->
            preparedStatement.setString(1, sykmeldtFnr)
            preparedStatement.setString(2, orgnummer)
            preparedStatement.executeQuery().use { resultSet ->
                generateSequence { if (resultSet.next()) resultSet else null }
                    .map { it.mapToOppfolgingsplan() }
                    .toList()
            }
        }
    }
}

fun DatabaseInterface.findOppfolgingsplanBy(
    uuid: UUID,
): PersistedOppfolgingsplan? {
    val statement = """
        SELECT *
        FROM oppfolgingsplan
        WHERE uuid = ?
    """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(statement).use { preparedStatement ->
            preparedStatement.setObject(1, uuid)
            val resultSet = preparedStatement.executeQuery()
            return if (resultSet.next()) {
                resultSet.mapToOppfolgingsplan()
            } else {
                null
            }
        }
    }
}

fun ResultSet.mapToOppfolgingsplan(): PersistedOppfolgingsplan {
    return PersistedOppfolgingsplan(
        uuid = getObject("uuid") as UUID,
        sykmeldtFnr = this.getString("sykemeldt_fnr"),
        narmesteLederId = this.getString("narmeste_leder_id"),
        narmesteLederFnr = this.getString("narmeste_leder_fnr"),
        orgnummer = this.getString("orgnummer"),
        content = ObjectMapper().readValue(getString("content")),
        sluttdato = LocalDate.parse(this.getString("sluttdato")),
        skalDelesMedLege = this.getBoolean("skal_deles_med_lege"),
        skalDelesMedVeileder = this.getBoolean("skal_deles_med_veileder"),
        deltMedLegeTidspunkt = this.getTimestamp("delt_med_lege_tidspunkt")?.toInstant(),
        deltMedVeilederTidspunkt = this.getTimestamp("delt_med_veileder_tidspunkt")?.toInstant(),
        createdAt = getTimestamp("created_at").toInstant(),
    )
}
