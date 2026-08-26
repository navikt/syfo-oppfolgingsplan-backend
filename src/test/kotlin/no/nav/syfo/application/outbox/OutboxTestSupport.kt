package no.nav.syfo.application.outbox

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.exposedTransaction
import no.nav.syfo.application.outbox.db.claimOutboxMessages
import no.nav.syfo.application.outbox.db.enqueueOutboxMessage
import no.nav.syfo.application.outbox.db.findOutboxMessage
import no.nav.syfo.application.outbox.domain.NewOutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

enum class TestOutboxMessageType(override val value: String) : OutboxMessageType {
    CREATED("TEST_OPPFOLGINGSPLAN_CREATED"),
    STAGED("TEST_STAGED_WITHOUT_HANDLER"),
}

val TEST_IMMEDIATE_MESSAGE = TestOutboxMessageType.CREATED
val TEST_SCHEDULED_MESSAGE = TestOutboxMessageType.CREATED
val TEST_STAGED_MESSAGE = TestOutboxMessageType.STAGED

suspend fun DatabaseInterface.enqueueTestOutboxMessage(
    messageType: OutboxMessageType = TEST_IMMEDIATE_MESSAGE,
    dedupKey: String = UUID.randomUUID().toString(),
    externalRef: String = UUID.randomUUID().toString(),
    payload: String = "{}",
    availableAt: Instant = Instant.EPOCH,
    uuid: UUID = UUID.randomUUID(),
): OutboxMessage = exposedTransaction {
    enqueueOutboxMessage(
        NewOutboxMessage(
            uuid = uuid,
            messageType = messageType,
            dedupKey = dedupKey,
            externalRef = externalRef,
            payload = payload,
            availableAt = availableAt,
        ),
    )
    requireNotNull(findOutboxMessage(messageType, dedupKey))
}

suspend fun DatabaseInterface.claim(
    now: Instant,
    limit: Int = 25,
): List<OutboxMessage> = exposedTransaction {
    claimOutboxMessages(TEST_IMMEDIATE_MESSAGE, now, limit, 5.minutes)
}

suspend fun DatabaseInterface.findOutboxMessage(message: OutboxMessage): OutboxMessage? = findOutboxMessage(message.messageType, message.dedupKey)

class TestOutboxHandler(
    override val messageType: OutboxMessageType = TEST_IMMEDIATE_MESSAGE,
    override val retryPolicy: OutboxRetryPolicy = ExponentialOutboxRetryPolicy(),
    private val outcome: suspend (OutboxMessage, Instant) -> OutboxResult = { _, _ -> OutboxResult.Sent },
) : OutboxMessageHandler {
    val handledMessages = mutableListOf<UUID>()

    override suspend fun handle(
        message: OutboxMessage,
        now: Instant,
    ): OutboxResult {
        handledMessages += message.uuid
        return outcome(message, now)
    }
}

class MutableClock(
    private var current: Instant,
    private val currentZone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    override fun getZone(): ZoneId = currentZone

    override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)

    override fun instant(): Instant = current

    fun advance(duration: java.time.Duration) {
        current = current.plus(duration)
    }
}
