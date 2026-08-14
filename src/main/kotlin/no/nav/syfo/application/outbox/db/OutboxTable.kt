package no.nav.syfo.application.outbox.db

import no.nav.syfo.application.outbox.domain.OutboxStatus
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

internal object OutboxTable : Table("outbox") {
    val uuid = javaUUID("uuid")
    val messageType = text("message_type")
    val dedupKey = text("dedup_key")
    val externalRef = text("external_ref")
    val payload = jsonb<String>("payload", { it }, { it })
    val availableAt = timestampWithTimeZone("available_at")
    val status = enumerationByName<OutboxStatus>("status", 32)
    val claimToken = javaUUID("claim_token").nullable()
    val leaseUntil = timestampWithTimeZone("lease_until").nullable()
    val failureCount = integer("failure_count")
    val lastFailureAt = timestampWithTimeZone("last_failure_at").nullable()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val completedAt = timestampWithTimeZone("completed_at").nullable()
    val cancellationReason = text("cancellation_reason").nullable()

    override val primaryKey = PrimaryKey(uuid)
}
