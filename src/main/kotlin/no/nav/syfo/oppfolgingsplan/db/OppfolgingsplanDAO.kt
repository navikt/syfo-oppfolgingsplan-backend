package no.nav.syfo.oppfolgingsplan.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.dinesykmeldte.client.getOrganizationName
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.dto.CreateOppfolgingsplanRequest
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.FormSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.jsonToFormSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.toJsonString
import java.math.BigDecimal
import java.sql.Date
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private const val OPPFOLGINGSPLAN_SOFT_DELETE_RETENTION_INTERVAL = "6 months"

fun DatabaseInterface.persistOppfolgingsplanAndDeleteUtkast(
    narmesteLederFnr: String,
    sykmeldt: Sykmeldt,
    createOppfolgingsplanRequest: CreateOppfolgingsplanRequest,
    stillingstittel: String?,
    stillingsprosent: BigDecimal?,
): UUID {
    val insertStatement = """
        INSERT INTO oppfolgingsplan (
            sykmeldt_fnr,
            sykmeldt_full_name,
            narmeste_leder_id,
            narmeste_leder_fnr,
            organisasjonsnummer,
            organisasjonsnavn,
            stillingstittel,
            stillingsprosent,
            content,
            evalueringsdato,
            skal_deles_med_lege,
            skal_deles_med_veileder,
            utkast_created_at,
            created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
        RETURNING uuid
    """.trimIndent()

    val deleteStatement = """
        DELETE FROM oppfolgingsplan_utkast
        WHERE narmeste_leder_id = ?
        RETURNING created_at
    """.trimIndent()

    connection.use { connection ->
        val utkastCreatedAt = connection.prepareStatement(deleteStatement).use {
            it.setString(1, sykmeldt.narmestelederId)
            val resultSet = it.executeQuery()
            if (resultSet.next()) {
                resultSet.getTimestamp("created_at").toInstant()
            } else {
                null
            }
        }

        val uuid = connection.prepareStatement(insertStatement).use {
            it.setString(1, sykmeldt.fnr)
            it.setString(2, sykmeldt.navn)
            it.setString(3, sykmeldt.narmestelederId)
            it.setString(4, narmesteLederFnr)
            it.setString(5, sykmeldt.orgnummer)
            it.setString(6, sykmeldt.getOrganizationName())
            it.setString(7, stillingstittel)
            it.setBigDecimal(8, stillingsprosent)
            it.setObject(9, createOppfolgingsplanRequest.content.toJsonString(), Types.OTHER)
            it.setDate(10, Date.valueOf(createOppfolgingsplanRequest.evalueringsdato.toString()))
            it.setBoolean(11, false)
            it.setBoolean(12, false)
            if (utkastCreatedAt != null) {
                it.setTimestamp(13, Timestamp.from(utkastCreatedAt))
            } else {
                it.setNull(13, Types.TIMESTAMP)
            }
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
    inkluderSkjulte: Boolean = false,
): List<PersistedOppfolgingsplan> {
    val skjultFilter = if (!inkluderSkjulte) "AND skjult_fra IS NULL" else ""
    val statement = """
        SELECT *
        FROM oppfolgingsplan
        WHERE sykmeldt_fnr = ?
        $skjultFilter
        ORDER BY created_at DESC
    """.trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(statement).use { preparedStatement ->
            preparedStatement.setString(1, sykmeldtFnr)
            preparedStatement.executeQuery().use { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(resultSet.mapToOppfolgingsplan())
                    }
                }
            }
        }
    }
}

fun DatabaseInterface.findAllOppfolgingsplanerBy(
    sykmeldtFnr: String,
    organisasjonsnummer: String,
): List<PersistedOppfolgingsplan> {
    val statement = """
        SELECT *
        FROM oppfolgingsplan
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
                        add(resultSet.mapToOppfolgingsplan())
                    }
                }
            }
        }
    }
}

fun DatabaseInterface.findOppfolgingsplanBy(
    uuid: UUID,
    inkluderSkjulte: Boolean = false,
): PersistedOppfolgingsplan? {
    val skjultFilter = if (!inkluderSkjulte) "AND skjult_fra IS NULL" else ""
    val statement = """
        SELECT *
        FROM oppfolgingsplan
        WHERE uuid = ?
        $skjultFilter
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

fun DatabaseInterface.setDeltMedVeilederTidspunkt(
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

fun DatabaseInterface.setJournalpostId(
    uuid: UUID,
    journalpostId: String,
) {
    val statement = """
        UPDATE oppfolgingsplan
        SET journalpost_id = ?
        WHERE uuid = ?
    """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(statement).use { preparedStatement ->
            preparedStatement.setString(1, journalpostId)
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

fun DatabaseInterface.updateDelingAvPlanMedVeileder(
    uuid: UUID,
    deltMedVeilederTidspunkt: Instant,
    journalpostId: String,
) {
    val statement = """
        UPDATE oppfolgingsplan
        SET skal_deles_med_veileder = true,
            delt_med_veileder_tidspunkt = ?,
            journalpost_id = ?
        WHERE uuid = ?
    """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(statement).use { preparedStatement ->
            preparedStatement.setTimestamp(1, Timestamp.from(deltMedVeilederTidspunkt))
            preparedStatement.setString(2, journalpostId)
            preparedStatement.setObject(3, uuid)
            preparedStatement.executeUpdate()
        }
        connection.commit()
    }
}

fun DatabaseInterface.findOppfolgingsplanerForDokumentportenPublisering(): List<PersistedOppfolgingsplan> {
    // Intentionally no filter on skjult_fra: hiding applies to SM/AG surfaces, while
    // Dokumentporten publication should still include plans hidden from those surfaces.
    val statement = """
        SELECT *
        FROM
            oppfolgingsplan
        WHERE
            sendt_til_dokumentporten_tidspunkt IS NULL
        LIMIT 100
    """.trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(statement).use { preparedStatement ->
            preparedStatement.executeQuery().use { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(resultSet.mapToOppfolgingsplan())
                    }
                }
            }
        }
    }
}

fun DatabaseInterface.setSendtTilDokumentportenTidspunkt(
    uuid: UUID,
    publisertTilDokumentportenTidspunkt: Instant,
) {
    val statement = """
        UPDATE oppfolgingsplan
        SET sendt_til_dokumentporten_tidspunkt = ?
        WHERE uuid = ?
    """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(statement).use { preparedStatement ->
            preparedStatement.setTimestamp(1, Timestamp.from(publisertTilDokumentportenTidspunkt))
            preparedStatement.setObject(2, uuid)
            preparedStatement.executeUpdate()
        }
        connection.commit()
    }
}

fun DatabaseInterface.softDeleteExpiredOppfolgingsplaner(
    batchSize: Int = 1000,
): Int {
    val statement = """
        WITH candidates AS (
            SELECT op.uuid
            FROM oppfolgingsplan op
            JOIN LATERAL (
                SELECT MAX(sp.tom) AS latest_tom
                FROM sykmeldingsperiode sp
                WHERE sp.sykmeldt_fnr = op.sykmeldt_fnr
                  AND sp.organisasjonsnummer = op.organisasjonsnummer
                  AND sp.invalidated_at IS NULL
            ) latest_valid_sykmeldingsperiode ON true
            WHERE op.skjult_fra IS NULL
              AND latest_valid_sykmeldingsperiode.latest_tom < CURRENT_DATE - CAST(? AS INTERVAL)
            ORDER BY op.uuid
            LIMIT ?
        )
        UPDATE oppfolgingsplan op
        SET skjult_fra = NOW()
        FROM candidates
        WHERE op.uuid = candidates.uuid
    """.trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(statement).use {
            it.setString(1, OPPFOLGINGSPLAN_SOFT_DELETE_RETENTION_INTERVAL)
            it.setInt(2, batchSize)
            it.executeUpdate()
        }.also { connection.commit() }
    }
}

fun ResultSet.mapToOppfolgingsplan(): PersistedOppfolgingsplan = PersistedOppfolgingsplan(
    uuid = getObject("uuid") as UUID,
    sykmeldtFnr = this.getString("sykmeldt_fnr"),
    sykmeldtFullName = this.getString("sykmeldt_full_name"),
    narmesteLederId = this.getString("narmeste_leder_id"),
    narmesteLederFnr = this.getString("narmeste_leder_fnr"),
    narmesteLederFullName = this.getString("narmeste_leder_full_name"),
    organisasjonsnummer = this.getString("organisasjonsnummer"),
    organisasjonsnavn = this.getString("organisasjonsnavn"),
    stillingstittel = this.getString("stillingstittel"),
    stillingsprosent = this.getBigDecimal("stillingsprosent"),
    content = FormSnapshot.jsonToFormSnapshot(getString("content")),
    evalueringsdato = LocalDate.parse(this.getString("evalueringsdato")),
    skalDelesMedLege = this.getBoolean("skal_deles_med_lege"),
    skalDelesMedVeileder = this.getBoolean("skal_deles_med_veileder"),
    deltMedLegeTidspunkt = this.getTimestamp("delt_med_lege_tidspunkt")?.toInstant(),
    journalpostId = this.getString("journalpost_id"),
    deltMedVeilederTidspunkt = this.getTimestamp("delt_med_veileder_tidspunkt")?.toInstant(),
    utkastCreatedAt = this.getTimestamp("utkast_created_at")?.toInstant(),
    createdAt = getTimestamp("created_at").toInstant(),
    skjultFra = this.getTimestamp("skjult_fra")?.toInstant(),
    sendtTilDokumentportenTidspunkt = this.getTimestamp("sendt_til_dokumentporten_tidspunkt")?.toInstant(),
)
