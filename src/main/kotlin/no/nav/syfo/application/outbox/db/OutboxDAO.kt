package no.nav.syfo.application.outbox.db

import no.nav.syfo.application.outbox.domain.OutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import no.nav.syfo.application.outbox.domain.OutboxStatus
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

fun Connection.insertOutboxMessage(
    uuid: UUID,
    messageType: OutboxMessageType,
    dedupKey: String,
    externalRef: String,
    payload: String,
    scheduledAt: Instant,
) {
    val statement = """
        INSERT INTO outbox (uuid, message_type, dedup_key, external_ref, payload, scheduled_at)
        VALUES (?, ?, ?, ?, ?::jsonb, ?)
    """.trimIndent()

    prepareStatement(statement).use {
        it.setObject(1, uuid)
        it.setString(2, messageType.name)
        it.setString(3, dedupKey)
        it.setString(4, externalRef)
        it.setString(5, payload)
        it.setTimestamp(6, Timestamp.from(scheduledAt))
        it.executeUpdate()
    }
}

fun Connection.claimNextReadyOutboxMessage(
    messageType: OutboxMessageType,
    now: Instant,
): OutboxMessage? {
    val statement = """
        SELECT *
        FROM outbox
        WHERE status = 'READY'
          AND message_type = ?
          AND scheduled_at <= ?
        ORDER BY scheduled_at
        LIMIT 1
        FOR UPDATE SKIP LOCKED
    """.trimIndent()

    prepareStatement(statement).use {
        it.setString(1, messageType.name)
        it.setTimestamp(2, Timestamp.from(now))
        it.executeQuery().use { resultSet ->
            return if (resultSet.next()) resultSet.toOutboxMessage() else null
        }
    }
}

fun Connection.markOutboxMessageSent(uuid: UUID, sentAt: Instant) {
    prepareStatement(
        "UPDATE outbox SET status = 'SENT', sent_at = ? WHERE uuid = ? AND status = 'READY'",
    ).use {
        it.setTimestamp(1, Timestamp.from(sentAt))
        it.setObject(2, uuid)
        check(it.executeUpdate() == 1) { "Ready outbox message was not marked sent" }
    }
}

fun Connection.markOutboxMessageIrrelevant(uuid: UUID) {
    prepareStatement(
        "UPDATE outbox SET status = 'IRRELEVANT' WHERE uuid = ? AND status = 'READY'",
    ).use {
        it.setObject(1, uuid)
        check(it.executeUpdate() == 1) { "Ready outbox message was not marked irrelevant" }
    }
}

fun Connection.recordOutboxMessageFailure(
    uuid: UUID,
    attemptedAt: Instant,
    retryAt: Instant,
    maxAttempts: Int,
) {
    prepareStatement(
        """
        UPDATE outbox
        SET attempt_count = attempt_count + 1,
            last_attempt_at = ?,
            scheduled_at = ?,
            status = CASE WHEN attempt_count + 1 >= ? THEN 'FAILED' ELSE 'READY' END
        WHERE uuid = ?
          AND status = 'READY'
        """.trimIndent(),
    ).use {
        it.setTimestamp(1, Timestamp.from(attemptedAt))
        it.setTimestamp(2, Timestamp.from(retryAt))
        it.setInt(3, maxAttempts)
        it.setObject(4, uuid)
        check(it.executeUpdate() == 1) { "Ready outbox message failure was not recorded" }
    }
}

fun Connection.deferOutboxMessage(
    uuid: UUID,
    retryAt: Instant,
) {
    prepareStatement(
        "UPDATE outbox SET scheduled_at = ? WHERE uuid = ? AND status = 'READY'",
    ).use {
        it.setTimestamp(1, Timestamp.from(retryAt))
        it.setObject(2, uuid)
        check(it.executeUpdate() == 1) { "Ready outbox message was not deferred" }
    }
}

fun Connection.findOutboxMessage(
    messageType: OutboxMessageType,
    dedupKey: String,
): OutboxMessage? {
    val statement = """
        SELECT *
        FROM outbox
        WHERE message_type = ?
          AND dedup_key = ?
    """.trimIndent()

    prepareStatement(statement).use {
        it.setString(1, messageType.name)
        it.setString(2, dedupKey)
        it.executeQuery().use { resultSet ->
            return if (resultSet.next()) resultSet.toOutboxMessage() else null
        }
    }
}

private fun ResultSet.toOutboxMessage(): OutboxMessage = OutboxMessage(
    uuid = getObject("uuid", UUID::class.java),
    messageType = OutboxMessageType.valueOf(getString("message_type")),
    dedupKey = getString("dedup_key"),
    externalRef = getString("external_ref"),
    payload = getString("payload"),
    scheduledAt = getTimestamp("scheduled_at").toInstant(),
    status = OutboxStatus.valueOf(getString("status")),
    attemptCount = getInt("attempt_count"),
    lastAttemptAt = getTimestamp("last_attempt_at")?.toInstant(),
    sentAt = getTimestamp("sent_at")?.toInstant(),
    createdAt = getTimestamp("created_at").toInstant(),
)
