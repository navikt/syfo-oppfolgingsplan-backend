package no.nav.syfo.application.outbox.domain

import java.time.Instant
import java.util.UUID

data class OutboxMessage(
    val uuid: UUID,
    val messageType: OutboxMessageType,
    val dedupKey: String,
    val externalRef: String,
    val payload: String,
    val scheduledAt: Instant,
    val status: OutboxStatus,
    val attemptCount: Int,
    val lastAttemptAt: Instant?,
    val sentAt: Instant?,
    val createdAt: Instant,
) {
    override fun toString(): String = "OutboxMessage(uuid=$uuid, messageType=$messageType, status=$status, scheduledAt=$scheduledAt)"
}

enum class OutboxMessageType {
    OPPFOLGINGSPLAN_OPPRETTET,
}

enum class OutboxStatus {
    READY,
    SENT,
    IRRELEVANT,
    FAILED,
}
