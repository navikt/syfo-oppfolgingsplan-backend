package no.nav.syfo.application.outbox

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.outbox.db.findOutboxByUuid
import no.nav.syfo.application.outbox.db.insertOutbox
import no.nav.syfo.application.outbox.domain.OutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import no.nav.syfo.application.outbox.domain.OutboxRelevans
import no.nav.syfo.application.outbox.domain.OutboxStatus
import java.sql.Connection
import java.time.Clock
import java.time.Instant
import java.util.UUID

fun <T> DatabaseInterface.execute(block: (Connection) -> T): T = connection.use { connection ->
    block(connection).also { connection.commit() }
}

fun DatabaseInterface.addOutboxMessage(
    dedupKey: String = UUID.randomUUID().toString(),
    messageType: OutboxMessageType = OutboxMessageType.PAAMINNELSE_OPPFOLGINGSPLAN,
    payload: String = """{"forlopFom":"2025-06-01"}""",
    scheduledAt: Instant = Instant.EPOCH,
): OutboxMessage = execute {
    it.insertOutbox(
        messageType = messageType,
        dedupKey = dedupKey,
        externalRef = UUID.randomUUID().toString(),
        payload = payload,
        scheduledAt = scheduledAt,
    )
}

fun DatabaseInterface.getOutboxMessage(uuid: UUID): OutboxMessage? = execute { it.findOutboxByUuid(uuid) }

fun DatabaseInterface.setOutboxStatus(uuid: UUID, status: OutboxStatus) = execute { connection ->
    connection.prepareStatement("UPDATE outbox SET status = ? WHERE uuid = ?").use { statement ->
        statement.setString(1, status.name)
        statement.setObject(2, uuid)
        statement.executeUpdate()
    }
}

class FakeOutboxHandler(
    override val messageType: OutboxMessageType = OutboxMessageType.PAAMINNELSE_OPPFOLGINGSPLAN,
    var relevant: (OutboxMessage) -> OutboxRelevans = { OutboxRelevans.Relevant },
    var onSend: (OutboxMessage) -> Unit = {},
) : OutboxMessageHandler {
    val sendteMeldinger = mutableListOf<UUID>()

    override fun evaluateRelevance(
        connection: Connection,
        message: OutboxMessage,
        now: Instant,
    ): OutboxRelevans = relevant(message)

    override fun send(connection: Connection, message: OutboxMessage) {
        sendteMeldinger += message.uuid
        onSend(message)
    }
}

fun testProcessor(
    handlers: List<OutboxMessageHandler>,
    clock: Clock,
    database: DatabaseInterface,
): OutboxProcessor = OutboxProcessor(database, handlers, clock)
