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
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Duration

/**
 * Inserts an immutable command once. Concurrent duplicate inserts can raise PostgreSQL 40001 under
 * REPEATABLE READ, so the surrounding pure database transaction must opt into replay with
 * `exposedTransaction(maxAttempts > 1)`.
 */
fun JdbcTransaction.enqueueOutboxMessage(message: NewOutboxMessage): Boolean = requireNotNull(
    exec(
        stmt = """
            INSERT INTO outbox (
                uuid, message_type, dedup_key, external_ref, payload, available_at, status, failure_count
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
            OutboxTable.availableAt.columnType to message.availableAt.atUtcOffset(),
        ),
        explicitStatementType = StatementType.SELECT,
    ) { resultSet -> resultSet.next() },
)

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
                    ((OutboxTable.status eq OutboxStatus.READY) and (OutboxTable.availableAt lessEq nowAtUtc)) or
                        (
                            (OutboxTable.status eq OutboxStatus.CLAIMED) and
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
        it[status] = OutboxStatus.CLAIMED
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
    sentAt: Instant,
): Boolean = updateClaimedMessage(uuid, claimToken) {
    it[status] = OutboxStatus.SENT
    it[OutboxTable.sentAt] = sentAt.atUtcOffset()
    it[OutboxTable.claimToken] = null
    it[leaseUntil] = null
}

fun JdbcTransaction.markOutboxMessageCancelled(
    uuid: UUID,
    claimToken: UUID,
    reason: OutboxCancellationReason,
): Boolean = updateClaimedMessage(uuid, claimToken) {
    it[status] = OutboxStatus.CANCELLED
    it[cancellationReason] = reason.value
    it[OutboxTable.claimToken] = null
    it[leaseUntil] = null
}

fun JdbcTransaction.deferOutboxMessage(
    uuid: UUID,
    claimToken: UUID,
    until: Instant,
): Boolean = updateClaimedMessage(uuid, claimToken) {
    it[status] = OutboxStatus.READY
    it[availableAt] = until.atUtcOffset()
    it[OutboxTable.claimToken] = null
    it[leaseUntil] = null
}

fun JdbcTransaction.recordOutboxMessageFailure(
    uuid: UUID,
    claimToken: UUID,
    failedAt: Instant,
    retryAt: Instant,
): Boolean = updateClaimedMessage(uuid, claimToken) {
    it[status] = OutboxStatus.READY
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
        (OutboxTable.status eq OutboxStatus.CLAIMED) and
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
    status = this[OutboxTable.status],
    claimToken = this[OutboxTable.claimToken],
    leaseUntil = this[OutboxTable.leaseUntil]?.toInstant(),
    failureCount = this[OutboxTable.failureCount],
    lastFailureAt = this[OutboxTable.lastFailureAt]?.toInstant(),
    createdAt = this[OutboxTable.createdAt].toInstant(),
    sentAt = this[OutboxTable.sentAt]?.toInstant(),
    cancellationReason = this[OutboxTable.cancellationReason]?.let(OutboxCancellationReason::fromDatabaseValue),
)

private fun Instant.atUtcOffset() = atOffset(ZoneOffset.UTC)
