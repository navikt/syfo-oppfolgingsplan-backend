package no.nav.syfo.outbox.domain

import java.util.UUID
import java.time.Instant

data class OutboxMessage (
    val uuid: UUID,
    val messageType: OutboxMessageType,
    val dedupKey: String,
    val externalRef: String,
    val payload: String,
    val scheduledAt: Instant,
    val status: OutboxMessageStatus,
    val attemptCount: Int,
    val lastAttemptAt: Instant?,
    val sentAt: Instant?,
    val createdAt: Instant,
)

enum class OutboxMessageStatus {
    READY,
    SENT,
    IRRELEVANT,
    FAILED
}

enum class OutboxMessageType {
    UNKNOWN,
}

