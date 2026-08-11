package no.nav.syfo.application.outbox

import no.nav.syfo.application.outbox.domain.OutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import no.nav.syfo.application.outbox.domain.OutboxRelevans
import java.sql.Connection
import java.time.Instant

/**
 * Delivery logic for one message type. The outbox core knows nothing about the domain.
 */
interface OutboxMessageHandler {
    val messageType: OutboxMessageType

    /** Decides whether the message should be delivered, discarded, or kept ready for later. */
    fun evaluateRelevance(connection: Connection, message: OutboxMessage, now: Instant): List<OutboxRelevans>

    fun send(connection: Connection, message: OutboxMessage)
}
