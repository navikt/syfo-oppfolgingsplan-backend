package no.nav.syfo.oppfolgingsplan.outbox

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.outbox.OutboxMessageHandler
import no.nav.syfo.application.outbox.OutboxResult
import no.nav.syfo.application.outbox.domain.OutboxCancellationReason
import no.nav.syfo.application.outbox.domain.OutboxMessage
import no.nav.syfo.oppfolgingsplan.db.PaaminnelseOutboxPayload
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedPaaminnelse
import no.nav.syfo.oppfolgingsplan.db.findPaaminnelseBy
import no.nav.syfo.oppfolgingsplan.service.PaaminnelseService
import no.nav.syfo.util.configuredJacksonMapper
import no.nav.syfo.util.logger
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

abstract class SharedPaaminnelseOutboxHandler(
    private val database: DatabaseInterface,
    private val paaminnelseService: PaaminnelseService,
) : OutboxMessageHandler {
    private val log = logger()

    final override suspend fun handle(
        message: OutboxMessage,
        now: Instant,
    ): OutboxResult {
        val paaminnelseUuid = message.externalRef.toUuid()
        val payload = configuredJacksonMapper.readValue<PaaminnelseOutboxPayload>(message.payload)
        val paaminnelse = withContext(Dispatchers.IO) {
            database.findPaaminnelseBy(paaminnelseUuid)
        } ?: return cancelMissingPaaminnelse(message)

        val paaminnelseStatus = withContext(Dispatchers.IO) {
            paaminnelseService.getOutboxPaaminnelseStatus(
                paaminnelse = paaminnelse,
                payload = payload,
                today = LocalDate.ofInstant(now, ZoneId.of("Europe/Oslo")),
            )
        }

        return when (paaminnelseStatus) {
            is PaaminnelseService.PaaminnelseStatusInternal.Utilgjengelig ->
                OutboxResult.Cancelled(paaminnelseStatus.reason)

            is PaaminnelseService.PaaminnelseStatusInternal.Tilgjengelig -> {
                publish(paaminnelse, payload, message.uuid)
                OutboxResult.Sent
            }
        }
    }

    private fun cancelMissingPaaminnelse(message: OutboxMessage): OutboxResult.Cancelled {
        log.error(
            "Cancelling outbox message because its source paaminnelse was not found {} {} {}",
            kv("outbox_uuid", message.uuid),
            kv("message_type", message.messageType.value),
            kv("cancellation_reason", OutboxCancellationReason.SOURCE_NOT_FOUND.value),
        )
        return OutboxResult.Cancelled(OutboxCancellationReason.SOURCE_NOT_FOUND)
    }

    protected abstract suspend fun publish(
        paaminnelse: PersistedPaaminnelse,
        payload: PaaminnelseOutboxPayload,
        eventId: UUID,
    )

    private fun String.toUuid(): UUID = try {
        UUID.fromString(this)
    } catch (error: IllegalArgumentException) {
        throw IllegalArgumentException("PaaminnelseOutbox externalRef must be a UUID", error)
    }
}
