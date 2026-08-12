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
    val createdAt: Instant,
    val sentAt: Instant?,
) {
    override fun toString(): String = "OutboxMessage(uuid=$uuid, messageType=$messageType, status=$status, " +
        "scheduledAt=$scheduledAt, attemptCount=$attemptCount)"
}

/**
 * Command for enqueuing one domain event. [dedupKey] and [externalRef] must use opaque identifiers,
 * and [payload] must contain only the minimum data needed by the handler. Never store national
 * identity numbers, organisation numbers, names, free text, or notification content here.
 */
data class NewOutboxMessage(
    val messageType: OutboxMessageType,
    val dedupKey: String,
    val externalRef: String,
    val payload: String,
    val scheduledAt: Instant,
    val uuid: UUID = UUID.randomUUID(),
) {
    init {
        require(dedupKey.isNotBlank()) { "dedupKey must not be blank" }
        require(externalRef.isNotBlank()) { "externalRef must not be blank" }
        require(payload.isNotBlank()) { "payload must not be blank" }
    }
}

@JvmInline
value class OutboxMessageType(val value: String) {
    init {
        require(TYPE_PATTERN.matches(value)) {
            "messageType must start with a letter and contain at most 64 uppercase letters, digits, or underscores"
        }
    }

    override fun toString(): String = value

    private companion object {
        val TYPE_PATTERN = Regex("[A-Z][A-Z0-9_]{0,63}")
    }
}

enum class OutboxStatus {
    READY,
    SENT,
    IRRELEVANT,

    /** Reserved for an explicit operator or future adapter decision; retries never set this. */
    FAILED,
}
