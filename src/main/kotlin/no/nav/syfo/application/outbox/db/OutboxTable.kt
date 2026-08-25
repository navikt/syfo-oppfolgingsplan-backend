package no.nav.syfo.application.outbox.db

import no.nav.syfo.application.outbox.domain.OutboxStatus
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

internal object OutboxTable : Table("outbox") {
    val uuid = javaUUID("uuid")
    val messageType = text("message_type")
    val dedupKey = text("dedup_key")
    val externalRef = text("external_ref")
    val payload = jsonb<String>("payload", { it }, { it })
    val availableAt = timestampWithTimeZone("available_at")
    val status = text("status").default(OutboxStatus.READY.name)
    val claimToken = javaUUID("claim_token").nullable()
    val leaseUntil = timestampWithTimeZone("lease_until").nullable()
    val failureCount = integer("failure_count").default(0)
    val lastFailureAt = timestampWithTimeZone("last_failure_at").nullable()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val completedAt = timestampWithTimeZone("completed_at").nullable()
    val cancellationReason = text("cancellation_reason").nullable()

    override val primaryKey = PrimaryKey(uuid)

    init {
        uniqueIndex("uq_outbox_message", messageType, dedupKey)
        index(
            customIndexName = "idx_outbox_ready",
            columns = arrayOf(messageType, availableAt, createdAt, uuid),
            filterCondition = { status eq OutboxStatus.READY.name },
        )
        index(
            customIndexName = "idx_outbox_ready_message_type_external_ref",
            columns = arrayOf(messageType, externalRef),
            filterCondition = { status eq OutboxStatus.READY.name },
        )
        index(
            customIndexName = "idx_outbox_expired_claim",
            columns = arrayOf(messageType, leaseUntil, createdAt, uuid),
            filterCondition = { status eq OutboxStatus.CLAIMED.name },
        )
        index(
            customIndexName = "idx_outbox_unresolved_failure",
            columns = arrayOf(messageType, failureCount),
            filterCondition = {
                (status inList listOf(OutboxStatus.READY.name, OutboxStatus.CLAIMED.name)) and
                    (failureCount greater 0)
            },
        )
        index(
            customIndexName = "idx_outbox_terminal_retention",
            columns = arrayOf(messageType, completedAt),
            filterCondition = { status inList listOf(OutboxStatus.SENT.name, OutboxStatus.CANCELLED.name) },
        )
        check("chk_outbox_status") { status inList OutboxStatus.entries.map(OutboxStatus::name) }
        check("chk_outbox_failure_metadata") {
            ((failureCount eq 0) and lastFailureAt.isNull()) or
                ((failureCount greater 0) and lastFailureAt.isNotNull())
        }
        check("chk_outbox_state_metadata") {
            (
                (status eq OutboxStatus.READY.name) and
                    claimToken.isNull() and
                    leaseUntil.isNull() and
                    completedAt.isNull() and
                    cancellationReason.isNull()
                ) or
                (
                    (status eq OutboxStatus.CLAIMED.name) and
                        claimToken.isNotNull() and
                        leaseUntil.isNotNull() and
                        completedAt.isNull() and
                        cancellationReason.isNull()
                    ) or
                (
                    (status eq OutboxStatus.SENT.name) and
                        claimToken.isNull() and
                        leaseUntil.isNull() and
                        completedAt.isNotNull() and
                        cancellationReason.isNull()
                    ) or
                (
                    (status eq OutboxStatus.CANCELLED.name) and
                        claimToken.isNull() and
                        leaseUntil.isNull() and
                        completedAt.isNotNull() and
                        cancellationReason.isNotNull()
                    )
        }
    }
}
