package no.nav.syfo.application.outbox.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.exposedTransaction
import no.nav.syfo.application.outbox.domain.OutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import no.nav.syfo.application.outbox.domain.OutboxStatus
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

fun JdbcTransaction.insertOutboxMessage(
    uuid: UUID,
    messageType: OutboxMessageType,
    dedupKey: String,
    externalRef: String,
    payload: String,
    scheduledAt: Instant,
) {
    OutboxTable.insert {
        it[OutboxTable.uuid] = uuid
        it[OutboxTable.messageType] = messageType
        it[OutboxTable.dedupKey] = dedupKey
        it[OutboxTable.externalRef] = externalRef
        it[OutboxTable.payload] = payload
        it[OutboxTable.scheduledAt] = scheduledAt.atOffset(ZoneOffset.UTC)
        it[OutboxTable.status] = OutboxStatus.READY
        it[OutboxTable.attemptCount] = 0
    }
}

fun JdbcTransaction.claimNextReadyOutboxMessage(
    messageType: OutboxMessageType,
    now: Instant,
): OutboxMessage? = OutboxTable
    .selectAll()
    .where {
        (OutboxTable.status eq OutboxStatus.READY) and
            (OutboxTable.messageType eq messageType) and
            (OutboxTable.scheduledAt lessEq now.atOffset(ZoneOffset.UTC))
    }.orderBy(OutboxTable.scheduledAt to SortOrder.ASC)
    .limit(1)
    .forUpdate(
        ForUpdateOption.PostgreSQL.ForUpdate(
            ForUpdateOption.PostgreSQL.MODE.SKIP_LOCKED,
        ),
    ).singleOrNull()
    ?.toOutboxMessage()

fun JdbcTransaction.markOutboxMessageSent(uuid: UUID, sentAt: Instant) {
    val updated = OutboxTable.update({
        (OutboxTable.uuid eq uuid) and (OutboxTable.status eq OutboxStatus.READY)
    }) {
        it[status] = OutboxStatus.SENT
        it[OutboxTable.sentAt] = sentAt.atOffset(ZoneOffset.UTC)
    }
    check(updated == 1) { "Ready outbox message was not marked sent" }
}

fun JdbcTransaction.markOutboxMessageIrrelevant(uuid: UUID) {
    val updated = OutboxTable.update({
        (OutboxTable.uuid eq uuid) and (OutboxTable.status eq OutboxStatus.READY)
    }) {
        it[status] = OutboxStatus.IRRELEVANT
    }
    check(updated == 1) { "Ready outbox message was not marked irrelevant" }
}

fun JdbcTransaction.recordOutboxMessageFailure(
    uuid: UUID,
    attemptedAt: Instant,
    retryAt: Instant,
    permanentlyFailed: Boolean,
) {
    val updated = OutboxTable.update({
        (OutboxTable.uuid eq uuid) and (OutboxTable.status eq OutboxStatus.READY)
    }) {
        it[attemptCount] = attemptCount + 1
        it[lastAttemptAt] = attemptedAt.atOffset(ZoneOffset.UTC)
        it[scheduledAt] = retryAt.atOffset(ZoneOffset.UTC)
        it[status] = if (permanentlyFailed) OutboxStatus.FAILED else OutboxStatus.READY
    }
    check(updated == 1) { "Ready outbox message failure was not recorded" }
}

fun JdbcTransaction.deferOutboxMessage(
    uuid: UUID,
    retryAt: Instant,
) {
    val updated = OutboxTable.update({
        (OutboxTable.uuid eq uuid) and (OutboxTable.status eq OutboxStatus.READY)
    }) {
        it[scheduledAt] = retryAt.atOffset(ZoneOffset.UTC)
    }
    check(updated == 1) { "Ready outbox message was not deferred" }
}

fun JdbcTransaction.findOutboxMessage(
    messageType: OutboxMessageType,
    dedupKey: String,
): OutboxMessage? = OutboxTable
    .selectAll()
    .where {
        (OutboxTable.messageType eq messageType) and
            (OutboxTable.dedupKey eq dedupKey)
    }.singleOrNull()
    ?.toOutboxMessage()

suspend fun DatabaseInterface.findOutboxMessage(
    messageType: OutboxMessageType,
    dedupKey: String,
): OutboxMessage? = exposedTransaction(readOnly = true) {
    this.findOutboxMessage(messageType, dedupKey)
}

private fun org.jetbrains.exposed.v1.core.ResultRow.toOutboxMessage(): OutboxMessage = OutboxMessage(
    uuid = this[OutboxTable.uuid],
    messageType = this[OutboxTable.messageType],
    dedupKey = this[OutboxTable.dedupKey],
    externalRef = this[OutboxTable.externalRef],
    payload = this[OutboxTable.payload],
    scheduledAt = this[OutboxTable.scheduledAt].toInstant(),
    status = this[OutboxTable.status],
    attemptCount = this[OutboxTable.attemptCount],
    lastAttemptAt = this[OutboxTable.lastAttemptAt]?.toInstant(),
    sentAt = this[OutboxTable.sentAt]?.toInstant(),
    createdAt = this[OutboxTable.createdAt].toInstant(),
)
