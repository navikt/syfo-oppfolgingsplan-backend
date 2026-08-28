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
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.sql.Connection
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Duration

data class OutboxQueueSnapshot(
    val dueReadyCount: Long,
    val oldestDueAt: Instant?,
    val expiredClaimCount: Long,
    val retryingCount: Long,
    val maxFailureCount: Int,
)

/**
 * Inserts an immutable command using the caller's existing JDBC transaction.
 *
 * This JDBC seam is intentional: adapters with existing JDBC persistence must enqueue on the same
 * [Connection] as their domain change to preserve the transactional-outbox guarantee. The function
 * never commits or opens a separate Exposed transaction; transaction ownership remains with the
 * caller. Independent outbox operations use the Exposed APIs below.
 */
fun Connection.enqueueOutboxMessage(message: NewOutboxMessage): Boolean = prepareStatement(
    """
        INSERT INTO outbox (
            uuid, message_type, dedup_key, external_ref, payload, available_at, status, failure_count
        )
        VALUES (?, ?, ?, ?, ?::jsonb, ?, 'READY', 0)
        ON CONFLICT (message_type, dedup_key) DO NOTHING
        RETURNING uuid
    """.trimIndent(),
).use { statement ->
    statement.setObject(1, message.uuid)
    statement.setString(2, message.messageType.value)
    statement.setString(3, message.dedupKey)
    statement.setString(4, message.externalRef)
    statement.setString(5, message.payload)
    statement.setObject(6, message.availableAt.atUtcOffset())
    statement.executeQuery().use { resultSet -> resultSet.next() }
}

/**
 * Cancels READY commands through the caller's existing JDBC transaction.
 *
 * The function never commits or opens a separate transaction, allowing a domain deactivation and
 * cancellation of its queued commands to become visible atomically.
 */
fun Connection.cancelReadyOutboxMessages(
    messageType: OutboxMessageType,
    externalRef: String,
    reason: OutboxCancellationReason,
    completedAt: Instant,
): Int = prepareStatement(
    """
        UPDATE outbox
        SET status = 'CANCELLED',
            cancellation_reason = ?,
            completed_at = ?
        WHERE message_type = ?
          AND external_ref = ?
          AND status = 'READY'
    """.trimIndent(),
).use { statement ->
    statement.setString(1, reason.value)
    statement.setObject(2, completedAt.atUtcOffset())
    statement.setString(3, messageType.value)
    statement.setString(4, externalRef)
    statement.executeUpdate()
}

/**
 * Exposed adapter for [Connection.enqueueOutboxMessage]. Under REPEATABLE READ, callers that can
 * encounter concurrent duplicate inserts may opt into replay when replaying the full transaction
 * is safe.
 */
fun JdbcTransaction.enqueueOutboxMessage(message: NewOutboxMessage): Boolean = (connection.connection as Connection)
    .enqueueOutboxMessage(message)

/**
 * Claims due READY rows and expired claims in one short transaction. The returned rows carry a new
 * claim token. Handler work must happen only after the transaction commits, and every later state
 * transition must present that token so a stale claimant cannot overwrite a newer claim.
 */
fun JdbcTransaction.claimOutboxMessages(
    messageType: OutboxMessageType,
    now: Instant,
    limit: Int,
    leaseDuration: Duration,
): List<OutboxMessage> {
    require(limit > 0) { "limit must be greater than zero" }
    require(leaseDuration.isFinite()) { "leaseDuration must be finite" }
    require(leaseDuration.inWholeMilliseconds > 0) { "leaseDuration must be at least one millisecond" }

    val nowAtUtc = now.atUtcOffset()
    val candidates = OutboxTable
        .selectAll()
        .where {
            (OutboxTable.messageType eq messageType.value) and
                (
                    ((OutboxTable.status eq OutboxStatus.READY.name) and (OutboxTable.availableAt lessEq nowAtUtc)) or
                        (
                            (OutboxTable.status eq OutboxStatus.CLAIMED.name) and
                                OutboxTable.leaseUntil.isNotNull() and
                                (OutboxTable.leaseUntil lessEq nowAtUtc)
                            )
                    )
        }
        .orderBy(
            OutboxTable.availableAt to SortOrder.ASC,
            OutboxTable.createdAt to SortOrder.ASC,
            OutboxTable.uuid to SortOrder.ASC,
        )
        .limit(limit)
        .forUpdate(
            ForUpdateOption.PostgreSQL.ForUpdate(
                ForUpdateOption.PostgreSQL.MODE.SKIP_LOCKED,
            ),
        )
        .toList()

    if (candidates.isEmpty()) return emptyList()

    val claimToken = UUID.randomUUID()
    val leaseUntil = now.plusMillis(leaseDuration.inWholeMilliseconds)
    val candidateIds = candidates.map { it[OutboxTable.uuid] }
    val updatedRows = OutboxTable.update({ OutboxTable.uuid inList candidateIds }) {
        it[status] = OutboxStatus.CLAIMED.name
        it[OutboxTable.claimToken] = claimToken
        it[OutboxTable.leaseUntil] = leaseUntil.atUtcOffset()
    }
    check(updatedRows == candidates.size) { "Expected to claim ${candidates.size} outbox rows, updated $updatedRows" }

    return candidates.map {
        it.toOutboxMessage(messageType).copy(
            status = OutboxStatus.CLAIMED,
            claimToken = claimToken,
            leaseUntil = leaseUntil,
        )
    }
}

fun JdbcTransaction.markOutboxMessageSent(
    uuid: UUID,
    claimToken: UUID,
    completedAt: Instant,
): Boolean = updateClaimedMessage(uuid, claimToken) {
    it[status] = OutboxStatus.SENT.name
    it[OutboxTable.completedAt] = completedAt.atUtcOffset()
    it[OutboxTable.claimToken] = null
    it[leaseUntil] = null
}

fun JdbcTransaction.markOutboxMessageCancelled(
    uuid: UUID,
    claimToken: UUID,
    reason: OutboxCancellationReason,
    completedAt: Instant,
): Boolean = updateClaimedMessage(uuid, claimToken) {
    it[status] = OutboxStatus.CANCELLED.name
    it[cancellationReason] = reason.value
    it[OutboxTable.completedAt] = completedAt.atUtcOffset()
    it[OutboxTable.claimToken] = null
    it[leaseUntil] = null
}

fun JdbcTransaction.deferOutboxMessage(
    uuid: UUID,
    claimToken: UUID,
    until: Instant,
): Boolean = updateClaimedMessage(uuid, claimToken) {
    it[status] = OutboxStatus.READY.name
    it[availableAt] = until.atUtcOffset()
    it[failureCount] = 0
    it[lastFailureAt] = null
    it[OutboxTable.claimToken] = null
    it[leaseUntil] = null
}

fun JdbcTransaction.recordOutboxMessageFailure(
    uuid: UUID,
    claimToken: UUID,
    failedAt: Instant,
    retryAt: Instant,
): Boolean = updateClaimedMessage(uuid, claimToken) {
    it[status] = OutboxStatus.READY.name
    it[failureCount] = failureCount + 1
    it[lastFailureAt] = failedAt.atUtcOffset()
    it[availableAt] = retryAt.atUtcOffset()
    it[OutboxTable.claimToken] = null
    it[leaseUntil] = null
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
    ?.toOutboxMessage(messageType)

fun JdbcTransaction.readOutboxQueueSnapshot(
    messageType: OutboxMessageType,
    now: Instant,
): OutboxQueueSnapshot = requireNotNull(
    exec(
        stmt = """
            SELECT
                (
                    SELECT count(*)
                    FROM outbox
                    WHERE message_type = ? AND status = 'READY' AND available_at <= ?
                ) AS due_ready_count,
                (
                    SELECT min(available_at)
                    FROM outbox
                    WHERE message_type = ? AND status = 'READY' AND available_at <= ?
                ) AS oldest_due_at,
                (
                    SELECT count(*)
                    FROM outbox
                    WHERE message_type = ? AND status = 'CLAIMED' AND lease_until <= ?
                ) AS expired_claim_count,
                (
                    SELECT count(*)
                    FROM outbox
                    WHERE message_type = ?
                      AND status IN ('READY', 'CLAIMED')
                      AND failure_count > 0
                ) AS retrying_count,
                (
                    SELECT COALESCE(max(failure_count), 0)
                    FROM outbox
                    WHERE message_type = ?
                      AND status IN ('READY', 'CLAIMED')
                      AND failure_count > 0
                ) AS max_failure_count
        """.trimIndent(),
        args = listOf(
            OutboxTable.messageType.columnType to messageType.value,
            OutboxTable.availableAt.columnType to now.atUtcOffset(),
            OutboxTable.messageType.columnType to messageType.value,
            OutboxTable.availableAt.columnType to now.atUtcOffset(),
            OutboxTable.messageType.columnType to messageType.value,
            OutboxTable.leaseUntil.columnType to now.atUtcOffset(),
            OutboxTable.messageType.columnType to messageType.value,
            OutboxTable.messageType.columnType to messageType.value,
        ),
        explicitStatementType = StatementType.SELECT,
    ) { resultSet ->
        resultSet.next()
        OutboxQueueSnapshot(
            dueReadyCount = resultSet.getLong("due_ready_count"),
            oldestDueAt = resultSet.getObject("oldest_due_at", java.time.OffsetDateTime::class.java)?.toInstant(),
            expiredClaimCount = resultSet.getLong("expired_claim_count"),
            retryingCount = resultSet.getLong("retrying_count"),
            maxFailureCount = resultSet.getInt("max_failure_count"),
        )
    },
)

fun JdbcTransaction.deleteTerminalOutboxMessages(
    messageType: OutboxMessageType,
    completedBefore: Instant,
    batchSize: Int,
): Int {
    require(batchSize > 0) { "batchSize must be greater than zero" }
    val statement = """
        WITH candidates AS (
            SELECT uuid
            FROM outbox
            WHERE message_type = ?
              AND status IN ('SENT', 'CANCELLED')
              AND completed_at <= ?
            ORDER BY completed_at, uuid
            LIMIT ?
            FOR UPDATE SKIP LOCKED
        )
        DELETE FROM outbox
        USING candidates
        WHERE outbox.uuid = candidates.uuid
    """.trimIndent()
    return (connection.connection as Connection).prepareStatement(statement).use {
        it.setString(1, messageType.value)
        it.setObject(2, completedBefore.atUtcOffset())
        it.setInt(3, batchSize)
        it.executeUpdate()
    }
}

internal suspend fun DatabaseInterface.findOutboxMessage(
    messageType: OutboxMessageType,
    dedupKey: String,
): OutboxMessage? = exposedTransaction(readOnly = true) {
    findOutboxMessage(messageType, dedupKey)
}

private fun JdbcTransaction.updateClaimedMessage(
    uuid: UUID,
    claimToken: UUID,
    body: OutboxTable.(UpdateStatement) -> Unit,
): Boolean = OutboxTable.update({
    (OutboxTable.uuid eq uuid) and
        (OutboxTable.status eq OutboxStatus.CLAIMED.name) and
        (OutboxTable.claimToken eq claimToken)
}, body = body) == 1

private fun ResultRow.toOutboxMessage(messageType: OutboxMessageType): OutboxMessage = OutboxMessage(
    uuid = this[OutboxTable.uuid],
    messageType = messageType.also {
        check(it.value == this[OutboxTable.messageType]) { "Outbox message type changed while reading a filtered row" }
    },
    dedupKey = this[OutboxTable.dedupKey],
    externalRef = this[OutboxTable.externalRef],
    payload = this[OutboxTable.payload],
    availableAt = this[OutboxTable.availableAt].toInstant(),
    status = OutboxStatus.valueOf(this[OutboxTable.status]),
    claimToken = this[OutboxTable.claimToken],
    leaseUntil = this[OutboxTable.leaseUntil]?.toInstant(),
    failureCount = this[OutboxTable.failureCount],
    lastFailureAt = this[OutboxTable.lastFailureAt]?.toInstant(),
    createdAt = this[OutboxTable.createdAt].toInstant(),
    completedAt = this[OutboxTable.completedAt]?.toInstant(),
    cancellationReason = this[OutboxTable.cancellationReason]?.let(OutboxCancellationReason::fromDatabaseValue),
)

private fun Instant.atUtcOffset() = atOffset(ZoneOffset.UTC)
