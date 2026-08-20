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
import no.nav.syfo.oppfolgingsplan.db.findPaaminnelseBy
import no.nav.syfo.util.configuredJacksonMapper
import no.nav.syfo.util.logger
import no.nav.syfo.varsel.budstikka.infrastructure.BudstikkaPublisher
import java.time.Instant
import java.util.UUID

class PaaminnelseOutboxHandler(
    private val database: DatabaseInterface,
    private val publisher: BudstikkaPublisher,
) : OutboxMessageHandler {
    private val log = logger()

    override val messageType = OppfolgingsplanOutboxMessageType.PAAMINNELSE

    override suspend fun handle(
        message: OutboxMessage,
        now: Instant,
    ): OutboxResult {
        val paaminnelseUuid = message.externalRef.toUuid()
        val payload = configuredJacksonMapper.readValue<PaaminnelseOutboxPayload>(message.payload)
        val paaminnelse = withContext(Dispatchers.IO) {
            database.findPaaminnelseBy(paaminnelseUuid)
        }

        return when {
            paaminnelse == null -> {
                log.error(
                    "Cancelling outbox message because its source paaminnelse was not found {} {} {}",
                    kv("outbox_uuid", message.uuid),
                    kv("message_type", message.messageType.value),
                    kv("cancellation_reason", OutboxCancellationReason.SOURCE_NOT_FOUND.value),
                )
                OutboxResult.Cancelled(OutboxCancellationReason.SOURCE_NOT_FOUND)
            }

            !paaminnelse.bestilt ||
                paaminnelse.sykmeldingsperiodeId != payload.sykmeldingsperiodeId ->
                OutboxResult.Cancelled(OutboxCancellationReason.SOURCE_NO_LONGER_ELIGIBLE)

            else -> {
                publisher.publishPaaminnelse(
                    paaminnelseUuid = paaminnelse.uuid,
                    eventId = message.uuid,
                    orgnummer = paaminnelse.organisasjonsnummer,
                    sykmeldtFnr = paaminnelse.sykmeldtFnr,
                    narmestelederId = payload.narmestelederId,
                )
                OutboxResult.Sent
            }
        }
    }

    private fun String.toUuid(): UUID = try {
        UUID.fromString(this)
    } catch (error: IllegalArgumentException) {
        throw IllegalArgumentException("PaaminnelseOutbox externalRef must be a UUID", error)
    }
}
