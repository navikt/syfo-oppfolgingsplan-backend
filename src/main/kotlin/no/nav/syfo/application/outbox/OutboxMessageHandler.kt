package no.nav.syfo.application.outbox

import no.nav.syfo.application.outbox.domain.OutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction

interface OutboxMessageHandler {
    val messageType: OutboxMessageType

    suspend fun process(
        transaction: JdbcTransaction,
        message: OutboxMessage,
    ): OutboxResult
}

enum class OutboxResult {
    SENT,
    IRRELEVANT,
    DEFERRED,
}
