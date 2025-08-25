package no.nav.syfo.oppfolgingsplan.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.dinesykmeldte.client.getOrganizationName
import no.nav.syfo.oppfolgingsplan.dto.CreateOppfolgingsplanRequest
import no.nav.syfo.oppfolgingsplan.dto.FormSnapshot
import no.nav.syfo.oppfolgingsplan.dto.jsonToFormSnapshot
import no.nav.syfo.oppfolgingsplan.dto.toJsonString
import java.sql.Date
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class PersistedOppfolgingsplan(
    val uuid: UUID,
    val sykmeldtFnr: String,
    val sykmeldtFullName: String,
    val narmesteLederId: String,
    val narmesteLederFnr: String,
    val narmesteLederFullName: String?,
    val organisasjonsnummer: String,
    val organisasjonsnavn: String?,
    val content: FormSnapshot,
    val sluttdato: LocalDate,
    val skalDelesMedLege: Boolean,
    val skalDelesMedVeileder: Boolean,
    val deltMedLegeTidspunkt: Instant? = null,
    val deltMedVeilederTidspunkt: Instant? = null,
    val createdAt: Instant
)

fun DatabaseInterface.persistOppfolgingsplanAndDeleteUtkast(
    narmesteLederFnr: String,
    sykmeldt: Sykmeldt,
    createOppfolgingsplanRequest: CreateOppfolgingsplanRequest
): UUID {
    val insertStatement = """
        INSERT INTO oppfolgingsplan (
            sykmeldt_fnr,
            sykmeldt_full_name,
            narmeste_leder_id,
            narmeste_leder_fnr,
            organisasjonsnummer,
            organisasjonsnavn,
            content,
            sluttdato,
            skal_deles_med_lege,
            skal_deles_med_veileder,
            created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
        RETURNING uuid
    """.trimIndent()

    val deleteStatement = """
        DELETE FROM oppfolgingsplan_utkast
        WHERE narmeste_leder_id = ?
    """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(deleteStatement).use {
            it.setString(1, sykmeldt.narmestelederId)
            it.executeUpdate()
        }
        val uuid = connection.prepareStatement(insertStatement).use {
            it.setString(1, sykmeldt.fnr)
            it.setString(2, sykmeldt.navn)
            it.setString(3, sykmeldt.narmestelederId)
            it.setString(4, narmesteLederFnr)
            it.setString(5, sykmeldt.orgnummer)
            it.setString(6, sykmeldt.getOrganizationName())
            it.setObject(7, createOppfolgingsplanRequest.content.toJsonString(), Types.OTHER)
            it.setDate(8, Date.valueOf(createOppfolgingsplanRequest.sluttdato.toString()))
            it.setBoolean(9, createOppfolgingsplanRequest.skalDelesMedLege)
            it.setBoolean(10, createOppfolgingsplanRequest.skalDelesMedVeileder)
            val resultSet = it.executeQuery()
            resultSet.next()
            resultSet.getObject("uuid", UUID::class.java)
        }
        connection.commit()
        return uuid
    }
}

fun DatabaseInterface.findAllOppfolgingsplanerBy(
    sykmeldtFnr: String,
): List<PersistedOppfolgingsplan> {
    val statement = """
        SELECT *
        FROM oppfolgingsplan
        WHERE sykmeldt_fnr = ?
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
    organisasjonsnummer: String
): List<PersistedOppfolgingsplan> {
    val statement = """
        SELECT *
        FROM oppfolgingsplan
        WHERE sykmeldt_fnr = ?
        AND organisasjonsnummer = ?
        ORDER BY created_at DESC
    """.trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(statement).use { preparedStatement ->
            preparedStatement.setString(1, sykmeldtFnr)
            preparedStatement.setString(2, organisasjonsnummer)
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

fun DatabaseInterface.updateSkalDelesMedLege(
    uuid: UUID,
    skalDelesMedLege: Boolean,
) {
    val statement = """
        UPDATE oppfolgingsplan
        SET skal_deles_med_lege = ?
        WHERE uuid = ?
    """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(statement).use { preparedStatement ->
            preparedStatement.setBoolean(1, skalDelesMedLege)
            preparedStatement.setObject(2, uuid)
            preparedStatement.executeUpdate()
        }
        connection.commit()
    }
}

fun DatabaseInterface.updateSkalDelesMedVeileder(
    uuid: UUID,
    skalDelesMedVeileder: Boolean,
) {
    val statement = """
        UPDATE oppfolgingsplan
        SET skal_deles_med_veileder = ?
        WHERE uuid = ?
    """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(statement).use { preparedStatement ->
            preparedStatement.setBoolean(1, skalDelesMedVeileder)
            preparedStatement.setObject(2, uuid)
            preparedStatement.executeUpdate()
        }
        connection.commit()
    }
}

fun DatabaseInterface.setDeltMedLegeTidspunkt(
    uuid: UUID,
    deltMedLegeTidspunkt: Instant,
) {
    val statement = """
        UPDATE oppfolgingsplan
        SET delt_med_lege_tidspunkt = ?
        WHERE uuid = ?
    """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(statement).use { preparedStatement ->
            preparedStatement.setTimestamp(1, Timestamp.from(deltMedLegeTidspunkt))
            preparedStatement.setObject(2, uuid)
            preparedStatement.executeUpdate()
        }
        connection.commit()
    }
}

fun DatabaseInterface.setDeltMedVeilderTidspunkt(
    uuid: UUID,
    deltMedVeilederTidspunkt: Instant,
) {
    val statement = """
        UPDATE oppfolgingsplan
        SET delt_med_veileder_tidspunkt = ?
        WHERE uuid = ?
    """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(statement).use { preparedStatement ->
            preparedStatement.setTimestamp(1, Timestamp.from(deltMedVeilederTidspunkt))
            preparedStatement.setObject(2, uuid)
            preparedStatement.executeUpdate()
        }
        connection.commit()
    }
}



fun DatabaseInterface.setNarmesteLederFullName(
    oppfolgingsplanUUID: UUID,
    narmesteLederFullName: String,
) {
    val statement = """
        UPDATE oppfolgingsplan
        SET narmeste_leder_full_name = ?
        WHERE uuid = ?
    """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(statement).use { preparedStatement ->
            preparedStatement.setString(1, narmesteLederFullName)
            preparedStatement.setObject(2, oppfolgingsplanUUID)
            preparedStatement.executeUpdate()
        }
        connection.commit()
    }
}

fun ResultSet.mapToOppfolgingsplan(): PersistedOppfolgingsplan {
    return PersistedOppfolgingsplan(
        uuid = getObject("uuid") as UUID,
        sykmeldtFnr = this.getString("sykmeldt_fnr"),
        sykmeldtFullName = this.getString("sykmeldt_full_name"),
        narmesteLederId = this.getString("narmeste_leder_id"),
        narmesteLederFnr = this.getString("narmeste_leder_fnr"),
        narmesteLederFullName = this.getString("narmeste_leder_full_name"),
        organisasjonsnummer = this.getString("organisasjonsnummer"),
        organisasjonsnavn = this.getString("organisasjonsnavn"),
        content = FormSnapshot.jsonToFormSnapshot(getString("content")),
        sluttdato = LocalDate.parse(this.getString("sluttdato")),
        skalDelesMedLege = this.getBoolean("skal_deles_med_lege"),
        skalDelesMedVeileder = this.getBoolean("skal_deles_med_veileder"),
        deltMedLegeTidspunkt = this.getTimestamp("delt_med_lege_tidspunkt")?.toInstant(),
        deltMedVeilederTidspunkt = this.getTimestamp("delt_med_veileder_tidspunkt")?.toInstant(),
        createdAt = getTimestamp("created_at").toInstant(),
    )
}
