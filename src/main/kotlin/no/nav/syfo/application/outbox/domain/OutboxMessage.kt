package no.nav.syfo.application.outbox.domain

import java.time.Instant
import java.util.UUID

data class OutboxMessage(
    val uuid: UUID,
    val messageTypeValue: String,
    val dedupKey: String,
    val externalRef: String,
    val payload: String,
    val scheduledAt: Instant,
    val status: OutboxStatus,
    val sendtAt: Instant?,
    val createdAt: Instant,
) {
    val messageType: OutboxMessageType?
        get() = OutboxMessageType.fromDbValue(messageTypeValue)
}

enum class OutboxStatus {
    KLAR,
    SENDT,
    IKKE_RELEVANT,
}

enum class OutboxMessageType {
    PAAMINNELSE_OPPFOLGINGSPLAN,
    ;

    companion object {
        fun fromDbValue(value: String): OutboxMessageType? = entries.firstOrNull { it.name == value }
    }
}
