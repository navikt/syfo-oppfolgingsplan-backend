package no.nav.syfo.oppfolgingsplan.outbox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.outbox.OutboxMessageHandler
import no.nav.syfo.application.outbox.OutboxResult
import no.nav.syfo.application.outbox.domain.OutboxCancellationReason
import no.nav.syfo.application.outbox.domain.OutboxMessage
import no.nav.syfo.oppfolgingsplan.db.OppfolgingsplanVarselSource
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanVarselSource
import no.nav.syfo.util.logger
import no.nav.syfo.varsel.budstikka.infrastructure.BudstikkaPublisher
import java.time.Instant
import java.util.UUID

class OppfolgingsplanCreatedOutboxHandler(
    private val database: DatabaseInterface,
    private val publisher: BudstikkaPublisher,
) : OutboxMessageHandler {
    private val log = logger()

    override val messageType = OppfolgingsplanOutboxMessageType.CREATED

    override suspend fun handle(
        message: OutboxMessage,
        now: Instant,
    ): OutboxResult {
        val oppfolgingsplanUuid = message.externalRef.toUuid()
        return when (
            val source = withContext(Dispatchers.IO) {
                database.findOppfolgingsplanVarselSource(oppfolgingsplanUuid)
            }
        ) {
            is OppfolgingsplanVarselSource.Eligible -> {
                publisher.publishOppfolgingsplanCreated(
                    oppfolgingsplanUuid = oppfolgingsplanUuid,
                    sykmeldtFnr = source.recipient.sykmeldtFnr,
                    eventId = message.uuid,
                )
                OutboxResult.Sent
            }
            OppfolgingsplanVarselSource.NoLongerEligible ->
                OutboxResult.Cancelled(OutboxCancellationReason.SOURCE_NO_LONGER_ELIGIBLE)
            OppfolgingsplanVarselSource.NotFound -> {
                log.error(
                    "Cancelling outbox message because its source oppfolgingsplan was not found {} {} {}",
                    kv("outbox_uuid", message.uuid),
                    kv("message_type", message.messageType.value),
                    kv("cancellation_reason", OutboxCancellationReason.SOURCE_NOT_FOUND.value),
                )
                OutboxResult.Cancelled(OutboxCancellationReason.SOURCE_NOT_FOUND)
            }
        }
    }

    private fun String.toUuid(): UUID = try {
        UUID.fromString(this)
    } catch (error: IllegalArgumentException) {
        throw IllegalArgumentException("Outbox externalRef must be an oppfolgingsplan UUID", error)
    }
}
