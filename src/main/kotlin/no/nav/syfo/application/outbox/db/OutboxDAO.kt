package no.nav.syfo.application.outbox.db

import no.nav.syfo.application.outbox.domain.OutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import no.nav.syfo.application.outbox.domain.OutboxStatus
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

fun Connection.claimNextReadyOutbox(
    messageType: OutboxMessageType,
    now: Instant,
): OutboxMessage? {
    val statement =
        """
        SELECT *
        FROM outbox
        WHERE status = 'KLAR'
          AND message_type = ?
          AND scheduled_at <= ?
        ORDER BY scheduled_at
        LIMIT 1
        FOR UPDATE SKIP LOCKED
        """.trimIndent()

    prepareStatement(statement).use { preparedStatement ->
        preparedStatement.setString(1, messageType.name)
        preparedStatement.setTimestamp(2, Timestamp.from(now))
        preparedStatement.executeQuery().use { resultSet ->
            return if (resultSet.next()) resultSet.toOutboxMessage() else null
        }
    }
}

fun Connection.insertOutbox(
    messageType: OutboxMessageType,
    dedupKey: String,
    externalRef: String,
    payload: String,
    scheduledAt: Instant,
): OutboxMessage {
    val statement =
        """
        INSERT INTO outbox (message_type, dedup_key, external_ref, payload, scheduled_at)
        VALUES (?, ?, ?, ?::jsonb, ?)
        ON CONFLICT (message_type, dedup_key) DO UPDATE
        SET status = CASE
                WHEN outbox.status = 'IKKE_RELEVANT' THEN 'KLAR'
                ELSE outbox.status
            END,
            sendt_at = CASE
                WHEN outbox.status = 'IKKE_RELEVANT' THEN NULL
                ELSE outbox.sendt_at
            END,
            scheduled_at = CASE
                WHEN outbox.status = 'IKKE_RELEVANT' THEN EXCLUDED.scheduled_at
                ELSE outbox.scheduled_at
            END
        RETURNING *
        """.trimIndent()

    prepareStatement(statement).use { preparedStatement ->
        preparedStatement.setString(1, messageType.name)
        preparedStatement.setString(2, dedupKey)
        preparedStatement.setString(3, externalRef)
        preparedStatement.setString(4, payload)
        preparedStatement.setTimestamp(5, Timestamp.from(scheduledAt))
        preparedStatement.executeQuery().use { resultSet ->
            resultSet.next()
            return resultSet.toOutboxMessage()
        }
    }
}

fun Connection.markOutboxSent(uuid: UUID, now: Instant) {
    prepareStatement(
        "UPDATE outbox SET status = 'SENDT', sendt_at = ? WHERE uuid = ? AND status = 'KLAR'",
    ).use { preparedStatement ->
        preparedStatement.setTimestamp(1, Timestamp.from(now))
        preparedStatement.setObject(2, uuid)
        preparedStatement.executeUpdate()
    }
}

fun Connection.markOutboxIkkeRelevant(uuid: UUID) {
    prepareStatement(
        "UPDATE outbox SET status = 'IKKE_RELEVANT' WHERE uuid = ? AND status = 'KLAR'",
    ).use { preparedStatement ->
        preparedStatement.setObject(1, uuid)
        preparedStatement.executeUpdate()
    }
}

fun Connection.findOutboxByUuid(uuid: UUID): OutboxMessage? {
    prepareStatement("SELECT * FROM outbox WHERE uuid = ?").use { preparedStatement ->
        preparedStatement.setObject(1, uuid)
        preparedStatement.executeQuery().use { resultSet ->
            return if (resultSet.next()) resultSet.toOutboxMessage() else null
        }
    }
}

fun Connection.findOutboxFor(messageType: OutboxMessageType, dedupKey: String): OutboxMessage? {
    prepareStatement("SELECT * FROM outbox WHERE message_type = ? AND dedup_key = ?").use { preparedStatement ->
        preparedStatement.setString(1, messageType.name)
        preparedStatement.setString(2, dedupKey)
        preparedStatement.executeQuery().use { resultSet ->
            return if (resultSet.next()) resultSet.toOutboxMessage() else null
        }
    }
}

private fun ResultSet.toOutboxMessage(): OutboxMessage = OutboxMessage(
    uuid = getObject("uuid", UUID::class.java),
    messageTypeValue = getString("message_type"),
    dedupKey = getString("dedup_key"),
    externalRef = getString("external_ref"),
    payload = getString("payload"),
    scheduledAt = getTimestamp("scheduled_at").toInstant(),
    status = OutboxStatus.valueOf(getString("status")),
    sendtAt = getTimestamp("sendt_at")?.toInstant(),
    createdAt = getTimestamp("created_at").toInstant(),
)
