package no.nav.syfo.application.outbox.db

import no.nav.syfo.application.outbox.domain.OutboxMessageType
import no.nav.syfo.application.outbox.domain.OutboxStatus
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

internal object OutboxTable : Table("outbox") {
    val uuid = javaUUID("uuid")
    val messageType = enumerationByName<OutboxMessageType>("message_type", 64)
    val dedupKey = text("dedup_key")
    val externalRef = text("external_ref")
    val payload = jsonb<String>("payload", { it }, { it })
    val scheduledAt = timestampWithTimeZone("scheduled_at")
    val status = enumerationByName<OutboxStatus>("status", 32)
    val attemptCount = integer("attempt_count")
    val lastAttemptAt = timestampWithTimeZone("last_attempt_at").nullable()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val sentAt = timestampWithTimeZone("sent_at").nullable()

    override val primaryKey = PrimaryKey(uuid)
}
