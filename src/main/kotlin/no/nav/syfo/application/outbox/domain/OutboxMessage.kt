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
    val cancellationReason: OutboxCancellationReason? = null,
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

/**
 * Closed set of commands supported by this application. [value] is the stable database contract;
 * enum constant names may therefore be refactored without rewriting persisted rows.
 */
enum class OutboxMessageType(val value: String) {
    OPPFOLGINGSPLAN_CREATED("OPPFOLGINGSPLAN_CREATED"),
    OPPFOLGINGSPLAN_FOUR_WEEK_REMINDER("OPPFOLGINGSPLAN_FOUR_WEEK_REMINDER"),
    OPPFOLGINGSPLAN_EVALUATION_REMINDER("OPPFOLGINGSPLAN_EVALUATION_REMINDER"),
    ;

    companion object {
        fun fromDatabaseValue(value: String): OutboxMessageType = entries.singleOrNull { it.value == value }
            ?: error("Unknown outbox message type: $value")
    }
}

enum class OutboxStatus {
    READY,
    SENT,
    CANCELLED,
}

/** Low-cardinality domain reason for deliberately not delivering a previously scheduled command. */
enum class OutboxCancellationReason(val value: String) {
    NO_LONGER_REQUESTED("NO_LONGER_REQUESTED"),
    SOURCE_NOT_FOUND("SOURCE_NOT_FOUND"),
    SOURCE_NO_LONGER_ELIGIBLE("SOURCE_NO_LONGER_ELIGIBLE"),
    PLAN_ALREADY_CREATED("PLAN_ALREADY_CREATED"),
    SUPERSEDED("SUPERSEDED"),
    NO_ELIGIBLE_RECIPIENT("NO_ELIGIBLE_RECIPIENT"),
    NO_RELEVANT_SICK_LEAVE("NO_RELEVANT_SICK_LEAVE"),
    ;

    companion object {
        fun fromDatabaseValue(value: String): OutboxCancellationReason = entries.singleOrNull { it.value == value }
            ?: error("Unknown outbox cancellation reason: $value")
    }
}
