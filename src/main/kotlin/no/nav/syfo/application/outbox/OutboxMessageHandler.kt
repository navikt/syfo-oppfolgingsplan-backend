package no.nav.syfo.application.outbox

import no.nav.syfo.application.outbox.domain.OutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import java.sql.Connection

interface OutboxMessageHandler {
    val messageType: OutboxMessageType

    suspend fun process(
        connection: Connection,
        message: OutboxMessage,
    ): OutboxResult
}

enum class OutboxResult {
    SENT,
    IRRELEVANT,
    DEFERRED,
}
