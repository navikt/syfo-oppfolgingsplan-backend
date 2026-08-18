package no.nav.syfo.application.outbox.domain

import java.time.Instant
import java.util.UUID

data class OutboxMessage(
    val uuid: UUID,
    val messageType: OutboxMessageType,
    val dedupKey: String,
    val externalRef: String,
    val payload: String,
    val availableAt: Instant,
    val status: OutboxStatus,
    val failureCount: Int,
    val lastFailureAt: Instant?,
    val createdAt: Instant,
    val completedAt: Instant?,
    val cancellationReason: OutboxCancellationReason? = null,
    val claimToken: UUID? = null,
    val leaseUntil: Instant? = null,
) {
    init {
        require(failureCount >= 0) { "failureCount must not be negative" }
        require((failureCount == 0) == (lastFailureAt == null)) {
            "lastFailureAt must be present exactly when failureCount is positive"
        }
        when (status) {
            OutboxStatus.READY -> require(completedAt == null && cancellationReason == null && claimToken == null && leaseUntil == null)
            OutboxStatus.CLAIMED -> require(completedAt == null && cancellationReason == null && claimToken != null && leaseUntil != null)
            OutboxStatus.SENT -> require(completedAt != null && cancellationReason == null && claimToken == null && leaseUntil == null)
            OutboxStatus.CANCELLED -> require(completedAt != null && cancellationReason != null && claimToken == null && leaseUntil == null)
        }
    }

    override fun toString(): String = "OutboxMessage(uuid=$uuid, messageType=$messageType, status=$status, " +
        "availableAt=$availableAt, failureCount=$failureCount)"
}

/**
 * Command for enqueuing one immutable domain event. [dedupKey] and [externalRef] must use opaque
 * identifiers, and [payload] must contain only the minimum data needed by the handler. Never store
 * national identity numbers, organisation numbers, names, free text, or notification content here.
 * A later opt-in creates a new command generation instead of mutating a terminal command.
 */
data class NewOutboxMessage(
    val messageType: OutboxMessageType,
    val dedupKey: String,
    val externalRef: String,
    val payload: String,
    val availableAt: Instant,
    val uuid: UUID = UUID.randomUUID(),
) {
    init {
        require(dedupKey.isNotBlank()) { "dedupKey must not be blank" }
        require(externalRef.isNotBlank()) { "externalRef must not be blank" }
        require(payload.isNotBlank()) { "payload must not be blank" }
    }
}

/**
 * Implemented by an adapter-owned enum whose explicit [value] is the stable database contract.
 * Keeping the enum with its adapter prevents the inactive core from advertising unsupported types.
 */
interface OutboxMessageType {
    val value: String
}

enum class OutboxStatus {
    READY,
    CLAIMED,
    SENT,
    CANCELLED,
}

/** General, low-cardinality reason for deliberately not delivering a scheduled command. */
enum class OutboxCancellationReason(val value: String) {
    NO_LONGER_REQUESTED("NO_LONGER_REQUESTED"),
    SOURCE_NOT_FOUND("SOURCE_NOT_FOUND"),
    SOURCE_NO_LONGER_ELIGIBLE("SOURCE_NO_LONGER_ELIGIBLE"),
    SUPERSEDED("SUPERSEDED"),
    ;

    companion object {
        fun fromDatabaseValue(value: String): OutboxCancellationReason = entries.singleOrNull { it.value == value }
            ?: error("Unknown outbox cancellation reason: $value")
    }
}
