package no.nav.syfo.application.outbox.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.exposedTransaction
import no.nav.syfo.application.outbox.domain.NewOutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxCancellationReason
import no.nav.syfo.application.outbox.domain.OutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import no.nav.syfo.application.outbox.domain.OutboxStatus
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * Inserts a message once. The unique message type + dedup key pair makes repeated domain commands
 * idempotent without replacing a terminal message.
 *
 * Returning only whether this call inserted avoids a second, snapshot-sensitive read when two
 * repeatable-read transactions enqueue the same command. Concurrent transactions can still get a
 * PostgreSQL serialization failure while resolving the unique conflict; the surrounding pure
 * database transaction must enable automatic replay through `exposedTransaction(maxAttempts > 1)`.
 */
fun JdbcTransaction.enqueueOutboxMessage(message: NewOutboxMessage): Boolean = requireNotNull(
    exec(
        stmt = """
            INSERT INTO outbox (
                uuid, message_type, dedup_key, external_ref, payload, scheduled_at, status, attempt_count
            )
            VALUES (?, ?, ?, ?, ?::jsonb, ?, 'READY', 0)
            ON CONFLICT (message_type, dedup_key) DO NOTHING
            RETURNING uuid
        """.trimIndent(),
        args = listOf(
            OutboxTable.uuid.columnType to message.uuid,
            OutboxTable.messageType.columnType to message.messageType.value,
            OutboxTable.dedupKey.columnType to message.dedupKey,
            OutboxTable.externalRef.columnType to message.externalRef,
            OutboxTable.payload.columnType to message.payload,
            OutboxTable.scheduledAt.columnType to message.scheduledAt.atUtcOffset(),
        ),
        // PostgreSQL RETURNING produces a result set even though this is an INSERT.
        explicitStatementType = StatementType.SELECT,
    ) { resultSet -> resultSet.next() },
)

/**
 * Explicitly schedules a previously cancelled command again, for example when a user opts back
 * into a reminder. Normal enqueue deliberately never changes a terminal message.
 */
fun JdbcTransaction.reactivateCancelledOutboxMessage(message: NewOutboxMessage): Boolean = OutboxTable.update({
    (OutboxTable.messageType eq message.messageType.value) and
        (OutboxTable.dedupKey eq message.dedupKey) and
        (OutboxTable.status eq OutboxStatus.CANCELLED)
}) {
    it[externalRef] = message.externalRef
    it[payload] = message.payload
    it[scheduledAt] = message.scheduledAt.atUtcOffset()
    it[status] = OutboxStatus.READY
    it[attemptCount] = 0
    it[lastAttemptAt] = null
    it[sentAt] = null
    it[cancellationReason] = null
} == 1

fun JdbcTransaction.claimNextReadyOutboxMessage(
    messageType: OutboxMessageType,
    now: Instant,
): OutboxMessage? = OutboxTable
    .selectAll()
    .where {
        (OutboxTable.status eq OutboxStatus.READY) and
            (OutboxTable.messageType eq messageType.value) and
            (OutboxTable.scheduledAt lessEq now.atUtcOffset())
    }
    .orderBy(
        OutboxTable.scheduledAt to SortOrder.ASC,
        OutboxTable.createdAt to SortOrder.ASC,
        OutboxTable.uuid to SortOrder.ASC,
    )
    .limit(1)
    .forUpdate(
        ForUpdateOption.PostgreSQL.ForUpdate(
            ForUpdateOption.PostgreSQL.MODE.SKIP_LOCKED,
        ),
    )
    .singleOrNull()
    ?.toOutboxMessage()

fun JdbcTransaction.markOutboxMessageSent(uuid: UUID, sentAt: Instant) {
    updateReadyMessage(uuid, "marked sent") {
        it[status] = OutboxStatus.SENT
        it[OutboxTable.sentAt] = sentAt.atUtcOffset()
    }
}

fun JdbcTransaction.markOutboxMessageCancelled(
    uuid: UUID,
    reason: OutboxCancellationReason,
) {
    updateReadyMessage(uuid, "marked cancelled") {
        it[status] = OutboxStatus.CANCELLED
        it[cancellationReason] = reason.value
    }
}

fun JdbcTransaction.deferOutboxMessage(uuid: UUID, until: Instant) {
    updateReadyMessage(uuid, "deferred") {
        it[scheduledAt] = until.atUtcOffset()
    }
}

fun JdbcTransaction.recordOutboxMessageFailure(
    uuid: UUID,
    failedAt: Instant,
    retryAt: Instant,
) {
    updateReadyMessage(uuid, "rescheduled after failure") {
        it[attemptCount] = attemptCount + 1
        it[lastAttemptAt] = failedAt.atUtcOffset()
        it[scheduledAt] = retryAt.atUtcOffset()
    }
}

fun JdbcTransaction.findOutboxMessage(
    messageType: OutboxMessageType,
    dedupKey: String,
): OutboxMessage? = OutboxTable
    .selectAll()
    .where {
        (OutboxTable.messageType eq messageType.value) and
            (OutboxTable.dedupKey eq dedupKey)
    }
    .singleOrNull()
    ?.toOutboxMessage()

fun JdbcTransaction.findOutboxMessage(uuid: UUID): OutboxMessage? = OutboxTable
    .selectAll()
    .where { OutboxTable.uuid eq uuid }
    .singleOrNull()
    ?.toOutboxMessage()

suspend fun DatabaseInterface.findOutboxMessage(
    messageType: OutboxMessageType,
    dedupKey: String,
): OutboxMessage? = exposedTransaction(readOnly = true) {
    findOutboxMessage(messageType, dedupKey)
}

suspend fun DatabaseInterface.findOutboxMessage(uuid: UUID): OutboxMessage? = exposedTransaction(readOnly = true) {
    findOutboxMessage(uuid)
}

private fun JdbcTransaction.updateReadyMessage(
    uuid: UUID,
    operation: String,
    body: OutboxTable.(UpdateStatement) -> Unit,
) {
    val updatedRows = OutboxTable.update({
        (OutboxTable.uuid eq uuid) and (OutboxTable.status eq OutboxStatus.READY)
    }, body = body)
    check(updatedRows == 1) { "Ready outbox message $uuid was not $operation" }
}

private fun ResultRow.toOutboxMessage(): OutboxMessage = OutboxMessage(
    uuid = this[OutboxTable.uuid],
    messageType = OutboxMessageType.fromDatabaseValue(this[OutboxTable.messageType]),
    dedupKey = this[OutboxTable.dedupKey],
    externalRef = this[OutboxTable.externalRef],
    payload = this[OutboxTable.payload],
    scheduledAt = this[OutboxTable.scheduledAt].toInstant(),
    status = this[OutboxTable.status],
    attemptCount = this[OutboxTable.attemptCount],
    lastAttemptAt = this[OutboxTable.lastAttemptAt]?.toInstant(),
    createdAt = this[OutboxTable.createdAt].toInstant(),
    sentAt = this[OutboxTable.sentAt]?.toInstant(),
    cancellationReason = this[OutboxTable.cancellationReason]?.let(OutboxCancellationReason::fromDatabaseValue),
)

private fun Instant.atUtcOffset() = atOffset(ZoneOffset.UTC)
