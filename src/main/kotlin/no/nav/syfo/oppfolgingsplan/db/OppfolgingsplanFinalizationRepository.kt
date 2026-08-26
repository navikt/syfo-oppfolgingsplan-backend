package no.nav.syfo.oppfolgingsplan.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.exposedTransaction
import no.nav.syfo.application.outbox.db.OutboxTable
import no.nav.syfo.application.outbox.db.enqueueOutboxMessage
import no.nav.syfo.application.outbox.domain.NewOutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxCancellationReason
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.dinesykmeldte.client.getOrganizationName
import no.nav.syfo.oppfolgingsplan.dto.CreateOppfolgingsplanRequest
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.toJsonString
import no.nav.syfo.oppfolgingsplan.outbox.EvalueringPaaminnelseDefinition
import no.nav.syfo.oppfolgingsplan.outbox.OppfolgingsplanOutboxMessageType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteReturning
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.json.jsonb
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class OppfolgingsplanFinalizationRepository(
    private val database: DatabaseInterface,
) {
    suspend fun finalize(command: OppfolgingsplanFinalizationCommand): OppfolgingsplanFinalizationResult = database.exposedTransaction(maxAttempts = 3) {
        val utkastCreatedAt = EvalueringPaaminnelseOppfolgingsplanUtkastTable
            .deleteReturning(returning = listOf(EvalueringPaaminnelseOppfolgingsplanUtkastTable.createdAt)) {
                EvalueringPaaminnelseOppfolgingsplanUtkastTable.narmesteLederId eq command.sykmeldt.narmestelederId
            }.singleOrNull()
            ?.get(EvalueringPaaminnelseOppfolgingsplanUtkastTable.createdAt)
        val eventId = UUID.randomUUID()

        val insertedOppfolgingsplanRow = EvalueringPaaminnelseOppfolgingsplanTable.insertReturning(
            returning = listOf(
                EvalueringPaaminnelseOppfolgingsplanTable.uuid,
                EvalueringPaaminnelseOppfolgingsplanTable.createdAt,
            ),
        ) {
            it[EvalueringPaaminnelseOppfolgingsplanTable.sykmeldtFnr] = command.sykmeldt.fnr
            it[EvalueringPaaminnelseOppfolgingsplanTable.sykmeldtFullName] = command.sykmeldt.navn
            it[EvalueringPaaminnelseOppfolgingsplanTable.narmesteLederId] = command.sykmeldt.narmestelederId
            it[EvalueringPaaminnelseOppfolgingsplanTable.narmesteLederFnr] = command.narmesteLederFnr
            it[EvalueringPaaminnelseOppfolgingsplanTable.organisasjonsnummer] = command.sykmeldt.orgnummer
            it[EvalueringPaaminnelseOppfolgingsplanTable.organisasjonsnavn] = command.sykmeldt.getOrganizationName()
            it[EvalueringPaaminnelseOppfolgingsplanTable.stillingstittel] = command.stillingstittel
            it[EvalueringPaaminnelseOppfolgingsplanTable.stillingsprosent] = command.stillingsprosent
            it[EvalueringPaaminnelseOppfolgingsplanTable.content] =
                command.createOppfolgingsplanRequest.content.toJsonString()
            it[EvalueringPaaminnelseOppfolgingsplanTable.evalueringsdato] =
                command.createOppfolgingsplanRequest.evalueringsdato
            it[EvalueringPaaminnelseOppfolgingsplanTable.evalueringPaaminnelse] =
                command.createOppfolgingsplanRequest.evalueringPaaminnelse
            it[EvalueringPaaminnelseOppfolgingsplanTable.evalueringPaaminnelseOutboxAt] = null
            it[EvalueringPaaminnelseOppfolgingsplanTable.skalDelesMedLege] = false
            it[EvalueringPaaminnelseOppfolgingsplanTable.skalDelesMedVeileder] = false
            it[EvalueringPaaminnelseOppfolgingsplanTable.utkastCreatedAt] = utkastCreatedAt
            it[EvalueringPaaminnelseOppfolgingsplanTable.createdAt] = CurrentTimestampWithTimeZone
            it[EvalueringPaaminnelseOppfolgingsplanTable.eventId] = eventId
        }.single()

        val oppfolgingsplanUuid = insertedOppfolgingsplanRow[EvalueringPaaminnelseOppfolgingsplanTable.uuid]
        val createdAt = insertedOppfolgingsplanRow[EvalueringPaaminnelseOppfolgingsplanTable.createdAt].toInstant()

        val supersededReminderCountByChannel = cancelReadySupersededEvalueringPaaminnelseRows(
            sykmeldtFnr = command.sykmeldt.fnr,
            organisasjonsnummer = command.sykmeldt.orgnummer,
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
        command.reminderDefinitions.forEach { definition ->
            check(
                enqueueOutboxMessage(
                    NewOutboxMessage(
                        messageType = definition.messageType,
                        dedupKey = oppfolgingsplanUuid.toString(),
                        externalRef = oppfolgingsplanUuid.toString(),
                        payload = "{}",
                        availableAt = definition.availableAt,
                    ),
                ),
            ) {
                "A new oppfolgingsplan with evalueringPaaminnelse must create one outbox command per channel"
            }
            createdReminderCountByChannel[definition.messageType] = 1
        }

        OppfolgingsplanFinalizationResult(
            oppfolgingsplanUuid = oppfolgingsplanUuid,
            createdReminderCountByChannel = createdReminderCountByChannel,
            supersededReminderCountByChannel = supersededReminderCountByChannel,
        )
    }

    private fun JdbcTransaction.cancelReadySupersededEvalueringPaaminnelseRows(
        sykmeldtFnr: String,
        organisasjonsnummer: String,
        supersedingOppfolgingsplanUuid: UUID,
        completedAt: Instant,
    ): Map<OppfolgingsplanOutboxMessageType, Int> {
        val countByMessageType = OppfolgingsplanOutboxMessageType.evalueringPaaminnelseTypes
            .associateWith { 0 }
            .toMutableMap()

        return exec(
            stmt = """
            WITH cancelled AS (
                UPDATE outbox
                SET status = 'CANCELLED',
                    completed_at = ?,
                    cancellation_reason = ?
                FROM oppfolgingsplan
                WHERE outbox.status = 'READY'
                  AND outbox.message_type IN (?, ?)
                  AND outbox.external_ref = oppfolgingsplan.uuid::text
                  AND oppfolgingsplan.sykmeldt_fnr = ?
                  AND oppfolgingsplan.organisasjonsnummer = ?
                  AND oppfolgingsplan.uuid <> ?
                RETURNING outbox.message_type
            )
            SELECT message_type, COUNT(*) AS cancelled_count
            FROM cancelled
            GROUP BY message_type
            """.trimIndent(),
            args = listOf(
                OutboxTable.completedAt.columnType to completedAt.atOffset(ZoneOffset.UTC),
                OutboxTable.cancellationReason.columnType to OutboxCancellationReason.SUPERSEDED.value,
                OutboxTable.messageType.columnType to
                    OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_MIN_SIDE_ARBEIDSGIVER.value,
                OutboxTable.messageType.columnType to
                    OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_DINE_SYKMELDTE.value,
                EvalueringPaaminnelseOppfolgingsplanTable.sykmeldtFnr.columnType to sykmeldtFnr,
                EvalueringPaaminnelseOppfolgingsplanTable.organisasjonsnummer.columnType to organisasjonsnummer,
                EvalueringPaaminnelseOppfolgingsplanTable.uuid.columnType to supersedingOppfolgingsplanUuid,
            ),
            explicitStatementType = StatementType.SELECT,
        ) { resultSet ->
            while (resultSet.next()) {
                val messageType = OppfolgingsplanOutboxMessageType.evalueringPaaminnelseTypes.single {
                    it.value == resultSet.getString("message_type")
                }
                countByMessageType[messageType] = resultSet.getInt("cancelled_count")
            }
            countByMessageType
        }
            ?: error("Cancellation query returned no result set")
    }
}

data class OppfolgingsplanFinalizationCommand(
    val narmesteLederFnr: String,
    val sykmeldt: Sykmeldt,
    val createOppfolgingsplanRequest: CreateOppfolgingsplanRequest,
    val stillingstittel: String?,
    val stillingsprosent: BigDecimal?,
    val reminderDefinitions: List<EvalueringPaaminnelseDefinition>,
)

data class OppfolgingsplanFinalizationResult(
    val oppfolgingsplanUuid: UUID,
    val createdReminderCountByChannel: Map<OppfolgingsplanOutboxMessageType, Int>,
    val supersededReminderCountByChannel: Map<OppfolgingsplanOutboxMessageType, Int>,
)

private object EvalueringPaaminnelseOppfolgingsplanTable : Table("oppfolgingsplan") {
    val uuid = javaUUID("uuid").databaseGenerated()
    val sykmeldtFnr = text("sykmeldt_fnr")
    val sykmeldtFullName = text("sykmeldt_full_name")
    val narmesteLederId = text("narmeste_leder_id")
    val narmesteLederFnr = text("narmeste_leder_fnr")
    val organisasjonsnummer = text("organisasjonsnummer")
    val organisasjonsnavn = text("organisasjonsnavn").nullable()
    val stillingstittel = text("stillingstittel").nullable()
    val stillingsprosent = decimal("stillingsprosent", 5, 2).nullable()
    val content = jsonb<String>("content", { it }, { it })
    val evalueringsdato = date("evalueringsdato")
    val evalueringPaaminnelse = bool("evaluering_paaminnelse")
    val evalueringPaaminnelseOutboxAt = timestampWithTimeZone("evaluering_paaminnelse_outbox_at").nullable()
    val skalDelesMedLege = bool("skal_deles_med_lege")
    val skalDelesMedVeileder = bool("skal_deles_med_veileder")
    val utkastCreatedAt = timestampWithTimeZone("utkast_created_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val eventId = javaUUID("event_id").nullable()

    override val primaryKey = PrimaryKey(uuid)
}

private object EvalueringPaaminnelseOppfolgingsplanUtkastTable : Table("oppfolgingsplan_utkast") {
    val uuid = javaUUID("uuid").databaseGenerated()
    val narmesteLederId = text("narmeste_leder_id")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(uuid)
}
