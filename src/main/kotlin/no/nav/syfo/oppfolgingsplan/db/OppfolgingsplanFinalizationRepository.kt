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
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteReturning
import org.jetbrains.exposed.v1.jdbc.insertReturning
import java.math.BigDecimal
import java.sql.Connection
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class OppfolgingsplanFinalizationRepository(
    private val database: DatabaseInterface,
) {
    suspend fun finalize(command: OppfolgingsplanFinalizationCommand): OppfolgingsplanFinalizationResult = database.exposedTransaction(
        maxAttempts = 3,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED,
    ) {
        acquireFinalizationLock(command.sykmeldt.fnr, command.sykmeldt.orgnummer)

        val utkastCreatedAt = OppfolgingsplanUtkastTable
            .deleteReturning(returning = listOf(OppfolgingsplanUtkastTable.createdAt)) {
                OppfolgingsplanUtkastTable.narmesteLederId eq command.sykmeldt.narmestelederId
            }.singleOrNull()
            ?.get(OppfolgingsplanUtkastTable.createdAt)
        val eventId = UUID.randomUUID()

        val insertedOppfolgingsplanRow = OppfolgingsplanTable.insertReturning(
            returning = listOf(
                OppfolgingsplanTable.uuid,
                OppfolgingsplanTable.createdAt,
            ),
        ) {
            it[OppfolgingsplanTable.sykmeldtFnr] = command.sykmeldt.fnr
            it[OppfolgingsplanTable.sykmeldtFullName] = command.sykmeldt.navn
            it[OppfolgingsplanTable.narmesteLederId] = command.sykmeldt.narmestelederId
            it[OppfolgingsplanTable.narmesteLederFnr] = command.narmesteLederFnr
            it[OppfolgingsplanTable.organisasjonsnummer] = command.sykmeldt.orgnummer
            it[OppfolgingsplanTable.organisasjonsnavn] = command.sykmeldt.getOrganizationName()
            it[OppfolgingsplanTable.stillingstittel] = command.stillingstittel
            it[OppfolgingsplanTable.stillingsprosent] = command.stillingsprosent
            it[OppfolgingsplanTable.content] =
                command.createOppfolgingsplanRequest.content.toJsonString()
            it[OppfolgingsplanTable.evalueringsdato] =
                command.createOppfolgingsplanRequest.evalueringsdato
            it[OppfolgingsplanTable.evalueringPaaminnelse] =
                command.createOppfolgingsplanRequest.evalueringPaaminnelse
            it[OppfolgingsplanTable.evalueringPaaminnelseOutboxAt] = null
            it[OppfolgingsplanTable.skalDelesMedLege] = false
            it[OppfolgingsplanTable.skalDelesMedVeileder] = false
            it[OppfolgingsplanTable.utkastCreatedAt] = utkastCreatedAt
            it[OppfolgingsplanTable.createdAt] = org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
            it[OppfolgingsplanTable.eventId] = eventId
        }.single()

        val oppfolgingsplanUuid = insertedOppfolgingsplanRow[OppfolgingsplanTable.uuid]
        val createdAt = insertedOppfolgingsplanRow[OppfolgingsplanTable.createdAt].toInstant()

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
                        availableAt = maxOf(definition.availableAt, createdAt),
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

    private fun JdbcTransaction.acquireFinalizationLock(
        sykmeldtFnr: String,
        organisasjonsnummer: String,
    ) {
        exec(
            stmt = "SELECT pg_advisory_xact_lock(hashtextextended(? || ':' || ?, 0))",
            args = listOf(
                OppfolgingsplanTable.sykmeldtFnr.columnType to sykmeldtFnr,
                OppfolgingsplanTable.organisasjonsnummer.columnType to organisasjonsnummer,
            ),
            explicitStatementType = StatementType.SELECT,
        ) { resultSet -> resultSet.next() }
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
                OppfolgingsplanTable.sykmeldtFnr.columnType to sykmeldtFnr,
                OppfolgingsplanTable.organisasjonsnummer.columnType to organisasjonsnummer,
                OppfolgingsplanTable.uuid.columnType to supersedingOppfolgingsplanUuid,
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
