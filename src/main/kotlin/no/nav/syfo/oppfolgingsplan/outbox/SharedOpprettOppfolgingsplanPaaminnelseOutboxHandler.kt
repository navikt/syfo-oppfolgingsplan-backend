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
import no.nav.syfo.oppfolgingsplan.db.OpprettOppfolgingsplanPaaminnelseOutboxPayload
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedOpprettOppfolgingsplanPaaminnelse
import no.nav.syfo.oppfolgingsplan.db.findOpprettOppfolgingsplanPaaminnelseBy
import no.nav.syfo.oppfolgingsplan.service.OpprettOppfolgingsplanPaaminnelseService
import no.nav.syfo.util.configuredJacksonMapper
import no.nav.syfo.util.logger
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

abstract class SharedOpprettOppfolgingsplanPaaminnelseOutboxHandler(
    private val database: DatabaseInterface,
    private val opprettOppfolgingsplanPaaminnelseService: OpprettOppfolgingsplanPaaminnelseService,
) : OutboxMessageHandler {
    private val log = logger()

    final override suspend fun handle(
        message: OutboxMessage,
        now: Instant,
    ): OutboxResult {
        val opprettOppfolgingsplanPaaminnelseUuid = message.externalRef.toUuid()
        val payload = configuredJacksonMapper.readValue<OpprettOppfolgingsplanPaaminnelseOutboxPayload>(message.payload)
        val opprettOppfolgingsplanPaaminnelse = withContext(Dispatchers.IO) {
            database.findOpprettOppfolgingsplanPaaminnelseBy(opprettOppfolgingsplanPaaminnelseUuid)
        } ?: return cancelMissingOpprettOppfolgingsplanPaaminnelse(message)

        val opprettOppfolgingsplanPaaminnelseStatus = withContext(Dispatchers.IO) {
            opprettOppfolgingsplanPaaminnelseService.getOutboxOpprettOppfolgingsplanPaaminnelseStatus(
                opprettOppfolgingsplanPaaminnelse = opprettOppfolgingsplanPaaminnelse,
                payload = payload,
                today = LocalDate.ofInstant(now, ZoneId.of("Europe/Oslo")),
            )
        }

        return when (opprettOppfolgingsplanPaaminnelseStatus) {
            is OpprettOppfolgingsplanPaaminnelseService.OpprettOppfolgingsplanPaaminnelseStatusInternal.Utilgjengelig ->
                OutboxResult.Cancelled(opprettOppfolgingsplanPaaminnelseStatus.reason)

            is OpprettOppfolgingsplanPaaminnelseService.OpprettOppfolgingsplanPaaminnelseStatusInternal.Tilgjengelig ->
                publish(opprettOppfolgingsplanPaaminnelse, payload, message.uuid)
        }
    }

    private fun cancelMissingOpprettOppfolgingsplanPaaminnelse(message: OutboxMessage): OutboxResult.Cancelled {
        log.error(
            "Cancelling outbox message because its source opprett oppfolgingsplan paaminnelse was not found {} {} {}",
            kv("outbox_uuid", message.uuid),
            kv("message_type", message.messageType.value),
            kv("cancellation_reason", OutboxCancellationReason.SOURCE_NOT_FOUND.value),
        )
        return OutboxResult.Cancelled(OutboxCancellationReason.SOURCE_NOT_FOUND)
    }

    protected abstract suspend fun publish(
        opprettOppfolgingsplanPaaminnelse: PersistedOpprettOppfolgingsplanPaaminnelse,
        payload: OpprettOppfolgingsplanPaaminnelseOutboxPayload,
        eventId: UUID,
    ): OutboxResult

    private fun String.toUuid(): UUID = try {
        UUID.fromString(this)
    } catch (error: IllegalArgumentException) {
        throw IllegalArgumentException("OpprettOppfolgingsplanPaaminnelseOutbox externalRef must be a UUID", error)
    }
}
