package no.nav.syfo.oppfolgingsplan.outbox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.outbox.OutboxMessageHandler
import no.nav.syfo.application.outbox.OutboxResult
import no.nav.syfo.application.outbox.domain.OutboxCancellationReason
import no.nav.syfo.application.outbox.domain.OutboxMessage
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanVarselRecipient
import no.nav.syfo.varsel.budstikka.infrastructure.BudstikkaPublisher
import java.time.Instant
import java.util.UUID

class OppfolgingsplanCreatedOutboxHandler(
    private val database: DatabaseInterface,
    private val publisher: BudstikkaPublisher,
) : OutboxMessageHandler {
    override val messageType = OppfolgingsplanOutboxMessageType.CREATED

    override suspend fun handle(
        message: OutboxMessage,
        now: Instant,
    ): OutboxResult {
        val oppfolgingsplanUuid = message.externalRef.toUuid()
        val recipient = withContext(Dispatchers.IO) {
            database.findOppfolgingsplanVarselRecipient(oppfolgingsplanUuid)
        } ?: return OutboxResult.Cancelled(OutboxCancellationReason.SOURCE_NO_LONGER_ELIGIBLE)

        publisher.publishOppfolgingsplanCreated(
            oppfolgingsplanUuid = oppfolgingsplanUuid,
            sykmeldtFnr = recipient.sykmeldtFnr,
            eventId = message.uuid,
        )
        return OutboxResult.Sent
    }

    private fun String.toUuid(): UUID = try {
        UUID.fromString(this)
    } catch (error: IllegalArgumentException) {
        throw IllegalArgumentException("Outbox externalRef must be an oppfolgingsplan UUID", error)
    }
}
