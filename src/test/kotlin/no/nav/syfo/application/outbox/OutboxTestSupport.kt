package no.nav.syfo.application.outbox

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.exposedTransaction
import no.nav.syfo.application.outbox.db.enqueueOutboxMessage
import no.nav.syfo.application.outbox.db.findOutboxMessage
import no.nav.syfo.application.outbox.domain.NewOutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import java.time.Instant
import java.util.UUID

val TEST_IMMEDIATE_MESSAGE = OutboxMessageType.OPPFOLGINGSPLAN_CREATED
val TEST_SCHEDULED_MESSAGE = OutboxMessageType.OPPFOLGINGSPLAN_FOUR_WEEK_REMINDER

suspend fun DatabaseInterface.enqueueTestOutboxMessage(
    messageType: OutboxMessageType = TEST_IMMEDIATE_MESSAGE,
    dedupKey: String = UUID.randomUUID().toString(),
    externalRef: String = UUID.randomUUID().toString(),
    payload: String = "{}",
    scheduledAt: Instant = Instant.EPOCH,
    uuid: UUID = UUID.randomUUID(),
): OutboxMessage = exposedTransaction {
    enqueueOutboxMessage(
        NewOutboxMessage(
            uuid = uuid,
            messageType = messageType,
            dedupKey = dedupKey,
            externalRef = externalRef,
            payload = payload,
            scheduledAt = scheduledAt,
        ),
    )
    requireNotNull(findOutboxMessage(messageType, dedupKey))
}

class TestOutboxHandler(
    override val messageType: OutboxMessageType = TEST_IMMEDIATE_MESSAGE,
    override val retryPolicy: OutboxRetryPolicy = ExponentialOutboxRetryPolicy(),
    private val outcome: suspend JdbcTransaction.(OutboxMessage, Instant) -> OutboxResult = { _, _ -> OutboxResult.Sent },
) : OutboxMessageHandler {
    val handledMessages = mutableListOf<UUID>()

    override suspend fun handle(
        transaction: JdbcTransaction,
        message: OutboxMessage,
        now: Instant,
    ): OutboxResult {
        handledMessages += message.uuid
        return transaction.outcome(message, now)
    }
}
