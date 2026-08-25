package no.nav.syfo.oppfolgingsplan.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.exposedTransaction
import no.nav.syfo.application.outbox.db.enqueueOutboxMessage
import no.nav.syfo.application.outbox.domain.NewOutboxMessage
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.dinesykmeldte.client.getOrganizationName
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.dto.CreateOppfolgingsplanRequest
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.FormSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.jsonToFormSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.toJsonString
import no.nav.syfo.oppfolgingsplan.outbox.OppfolgingsplanEvalueringPaaminnelseOutboxMetrics
import no.nav.syfo.oppfolgingsplan.outbox.OppfolgingsplanOutboxMessageType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteReturning
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

private fun logger() = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
private val ZONE_OSLO: ZoneId = ZoneId.of("Europe/Oslo")
private const val EVALUERING_PAAMINNELSE_DAYS_BEFORE = 3L
private const val EVALUERING_PAAMINNELSE_HOUR = 9

suspend fun DatabaseInterface.persistOppfolgingsplanAndDeleteUtkast(
    narmesteLederFnr: String,
    sykmeldt: Sykmeldt,
    createOppfolgingsplanRequest: CreateOppfolgingsplanRequest,
    stillingstittel: String?,
    stillingsprosent: BigDecimal?,
): UUID {
    val persistResult = exposedTransaction {
        val utkastCreatedAt = OppfolgingsplanUtkastTable
            .deleteReturning(returning = listOf(OppfolgingsplanUtkastTable.createdAt)) {
                OppfolgingsplanUtkastTable.narmesteLederId eq sykmeldt.narmestelederId
            }.singleOrNull()
            ?.get(OppfolgingsplanUtkastTable.createdAt)
        val eventId = UUID.randomUUID()

        val insertedOppfolgingsplanRow = OppfolgingsplanTable.insertReturning(
            returning = listOf(
                OppfolgingsplanTable.uuid,
                OppfolgingsplanTable.createdAt,
            ),
        ) {
            it[OppfolgingsplanTable.sykmeldtFnr] = sykmeldt.fnr
            it[OppfolgingsplanTable.sykmeldtFullName] = sykmeldt.navn
            it[OppfolgingsplanTable.narmesteLederId] = sykmeldt.narmestelederId
            it[OppfolgingsplanTable.narmesteLederFnr] = narmesteLederFnr
            it[OppfolgingsplanTable.organisasjonsnummer] = sykmeldt.orgnummer
            it[OppfolgingsplanTable.organisasjonsnavn] = sykmeldt.getOrganizationName()
            it[OppfolgingsplanTable.stillingstittel] = stillingstittel
            it[OppfolgingsplanTable.stillingsprosent] = stillingsprosent
            it[OppfolgingsplanTable.content] = createOppfolgingsplanRequest.content.toJsonString()
            it[OppfolgingsplanTable.evalueringsdato] = createOppfolgingsplanRequest.evalueringsdato
            it[OppfolgingsplanTable.evalueringPaaminnelse] = createOppfolgingsplanRequest.evalueringPaaminnelse
            it[OppfolgingsplanTable.evalueringPaaminnelseOutboxAt] = null
            it[OppfolgingsplanTable.skalDelesMedLege] = false
            it[OppfolgingsplanTable.skalDelesMedVeileder] = false
            it[OppfolgingsplanTable.utkastCreatedAt] = utkastCreatedAt
            it[OppfolgingsplanTable.createdAt] = CurrentTimestampWithTimeZone
            it[OppfolgingsplanTable.eventId] = eventId
        }.single()

        val oppfolgingsplanUuid = insertedOppfolgingsplanRow[OppfolgingsplanTable.uuid]
        val createdAt = insertedOppfolgingsplanRow[OppfolgingsplanTable.createdAt].toInstant()

        val supersededReminderCountByChannel = cancelReadySupersededEvalueringPaaminnelseRows(
            sykmeldtFnr = sykmeldt.fnr,
            organisasjonsnummer = sykmeldt.orgnummer,
            supersedingOppfolgingsplanUuid = oppfolgingsplanUuid,
            completedAt = createdAt,
        )

        check(
            enqueueOutboxMessage(
                NewOutboxMessage(
                    uuid = eventId,
                    messageType = OppfolgingsplanOutboxMessageType.CREATED,
                    dedupKey = oppfolgingsplanUuid.toString(),
                    externalRef = oppfolgingsplanUuid.toString(),
                    payload = "{}",
                    availableAt = createdAt,
                ),
            ),
        ) { "A new oppfolgingsplan must create a new outbox command" }

        val createdReminderCountByChannel = OppfolgingsplanOutboxMessageType.evalueringPaaminnelseTypes
            .associateWith { 0 }
            .toMutableMap()
        if (createOppfolgingsplanRequest.evalueringPaaminnelse) {
            OppfolgingsplanOutboxMessageType.evalueringPaaminnelseTypes.forEach { channelMessageType ->
                check(
                    enqueueOutboxMessage(
                        NewOutboxMessage(
                            messageType = channelMessageType,
                            dedupKey = oppfolgingsplanUuid.toString(),
                            externalRef = oppfolgingsplanUuid.toString(),
                            payload = "{}",
                            availableAt = createOppfolgingsplanRequest.evalueringsdato
                                .toEvalueringPaaminnelseAvailableAt(),
                        ),
                    ),
                ) {
                    "A new oppfolgingsplan with evalueringPaaminnelse must create one outbox command per channel"
                }
                createdReminderCountByChannel[channelMessageType] = 1
            }
        }

        PersistOppfolgingsplanResult(
            oppfolgingsplanUuid = oppfolgingsplanUuid,
            createdReminderCountByChannel = createdReminderCountByChannel,
            supersededReminderCountByChannel = supersededReminderCountByChannel,
        )
    }

    OppfolgingsplanEvalueringPaaminnelseOutboxMetrics.incrementCreated(
        persistResult.createdReminderCountByChannel,
    )
    OppfolgingsplanEvalueringPaaminnelseOutboxMetrics.incrementSuperseded(
        persistResult.supersededReminderCountByChannel,
    )
    return persistResult.oppfolgingsplanUuid
}

private data class PersistOppfolgingsplanResult(
    val oppfolgingsplanUuid: UUID,
    val createdReminderCountByChannel: Map<OppfolgingsplanOutboxMessageType, Int>,
    val supersededReminderCountByChannel: Map<OppfolgingsplanOutboxMessageType, Int>,
)

private fun LocalDate.toEvalueringPaaminnelseAvailableAt(): Instant = minusDays(EVALUERING_PAAMINNELSE_DAYS_BEFORE)
    .atTime(EVALUERING_PAAMINNELSE_HOUR, 0)
    .atZone(ZONE_OSLO)
    .toInstant()

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
            it.setString(1, SOFT_DELETE_RETENTION_INTERVAL)
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
    evalueringPaaminnelse = this.getBoolean("evaluering_paaminnelse"),
    evalueringPaaminnelseOutboxAt = this.getTimestamp("evaluering_paaminnelse_outbox_at")?.toInstant(),
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
