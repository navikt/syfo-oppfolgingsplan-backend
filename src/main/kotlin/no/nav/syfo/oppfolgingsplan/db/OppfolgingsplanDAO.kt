package no.nav.syfo.oppfolgingsplan.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.outbox.domain.OutboxCancellationReason
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.FormSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.jsonToFormSnapshot
import no.nav.syfo.oppfolgingsplan.outbox.OppfolgingsplanOutboxMessageType
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private fun logger() = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

fun DatabaseInterface.findAllOppfolgingsplanerBy(
    sykmeldtFnr: String,
    inkluderSkjulte: Boolean = false,
): List<PersistedOppfolgingsplan> {
    val skjultFilter = if (!inkluderSkjulte) "AND skjult_fra IS NULL" else ""
    val statement = """
        SELECT *
        FROM oppfolgingsplan
        WHERE sykmeldt_fnr = ?
        AND feilregistrert IS NULL
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
        AND feilregistrert IS NULL
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

fun DatabaseInterface.existsOppfolgingsplanCreatedAfter(
    sykmeldtFnr: String,
    organisasjonsnummer: String,
    createdAfter: Instant,
): Boolean {
    val statement = """
        SELECT EXISTS (
            SELECT 1
            FROM oppfolgingsplan
            WHERE sykmeldt_fnr = ?
            AND organisasjonsnummer = ?
            AND created_at >= ?
            AND skjult_fra IS NULL
            AND feilregistrert IS NULL
        )
    """.trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(statement).use { preparedStatement ->
            preparedStatement.setString(1, sykmeldtFnr)
            preparedStatement.setString(2, organisasjonsnummer)
            preparedStatement.setTimestamp(3, Timestamp.from(createdAfter))
            preparedStatement.executeQuery().use { resultSet ->
                resultSet.next()
                resultSet.getBoolean(1)
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
        AND feilregistrert IS NULL
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
): Int = softDeleteExpiredOppfolgingsplanerWithResult(batchSize).hiddenCount

data class SoftDeleteExpiredOppfolgingsplanerResult(
    val hiddenCount: Int,
    val cancelledReminderCountByChannel: Map<OppfolgingsplanOutboxMessageType, Int>,
)

fun DatabaseInterface.softDeleteExpiredOppfolgingsplanerWithResult(
    batchSize: Int = 1000,
): SoftDeleteExpiredOppfolgingsplanerResult {
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
        , hidden AS (
            UPDATE oppfolgingsplan op
            SET skjult_fra = NOW()
            FROM candidates
            WHERE op.uuid = candidates.uuid
            RETURNING op.uuid
        ), cancelled AS (
            UPDATE outbox
            SET status = 'CANCELLED',
                cancellation_reason = ?,
                completed_at = NOW()
            FROM hidden
            WHERE outbox.external_ref = hidden.uuid::text
              AND outbox.message_type IN (?, ?)
              AND outbox.status = 'READY'
            RETURNING outbox.message_type
        )
        SELECT
            (SELECT COUNT(*) FROM hidden) AS hidden_count,
            COUNT(*) FILTER (WHERE message_type = ?) AS min_side_arbeidsgiver_cancelled_count,
            COUNT(*) FILTER (WHERE message_type = ?) AS dine_sykmeldte_cancelled_count
        FROM cancelled
    """.trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(statement).use {
            it.setString(1, SOFT_DELETE_RETENTION_INTERVAL)
            it.setInt(2, batchSize)
            it.setString(3, OutboxCancellationReason.SOURCE_NO_LONGER_ELIGIBLE.value)
            it.setString(
                4,
                OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_MIN_SIDE_ARBEIDSGIVER.value,
            )
            it.setString(
                5,
                OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_DINE_SYKMELDTE.value,
            )
            it.setString(
                6,
                OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_MIN_SIDE_ARBEIDSGIVER.value,
            )
            it.setString(
                7,
                OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_DINE_SYKMELDTE.value,
            )
            it.executeQuery().use { resultSet ->
                resultSet.next()
                SoftDeleteExpiredOppfolgingsplanerResult(
                    hiddenCount = resultSet.getInt("hidden_count"),
                    cancelledReminderCountByChannel = mapOf(
                        OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_MIN_SIDE_ARBEIDSGIVER to
                            resultSet.getInt("min_side_arbeidsgiver_cancelled_count"),
                        OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_DINE_SYKMELDTE to
                            resultSet.getInt("dine_sykmeldte_cancelled_count"),
                    ),
                )
            }
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
    evalueringPaaminnelse = this.getBoolean("evaluering_paaminnelse"),
    skalDelesMedLege = this.getBoolean("skal_deles_med_lege"),
    skalDelesMedVeileder = this.getBoolean("skal_deles_med_veileder"),
    deltMedLegeTidspunkt = this.getTimestamp("delt_med_lege_tidspunkt")?.toInstant(),
    journalpostId = this.getString("journalpost_id"),
    deltMedVeilederTidspunkt = this.getTimestamp("delt_med_veileder_tidspunkt")?.toInstant(),
    utkastCreatedAt = this.getTimestamp("utkast_created_at")?.toInstant(),
    createdAt = getTimestamp("created_at").toInstant(),
    skjultFra = this.getTimestamp("skjult_fra")?.toInstant(),
    feilregistrertAarsak = this.getString("feilregistrert_aarsak"),
    feilregistrert = this.getTimestamp("feilregistrert")?.toInstant(),
    sendtTilDokumentportenTidspunkt = this.getTimestamp("sendt_til_dokumentporten_tidspunkt")?.toInstant(),
)
