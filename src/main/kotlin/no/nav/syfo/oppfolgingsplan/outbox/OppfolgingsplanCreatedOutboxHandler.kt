package no.nav.syfo.oppfolgingsplan.outbox

import no.nav.budstikka.contract.Budstikka
import no.nav.budstikka.contract.EventId
import no.nav.budstikka.contract.PersonIdentifier
import no.nav.budstikka.contract.Varseltype
import no.nav.syfo.application.outbox.OutboxMessageHandler
import no.nav.syfo.application.outbox.OutboxResult
import no.nav.syfo.application.outbox.domain.OutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanVarselRecipient
import no.nav.syfo.varsel.budstikka.infrastructure.BudstikkaPublisher
import java.sql.Connection
import java.util.UUID

const val OPPFOLGINGSPLAN_CREATED_BUDSTIKKA_TEXT = "Din arbeidsgiver har laget en oppfølgingsplan for deg"

class OppfolgingsplanCreatedOutboxHandler(
    private val publisher: BudstikkaPublisher,
    private val oppfolgingsplanUrl: String,
) : OutboxMessageHandler {
    override val messageType: OutboxMessageType = OutboxMessageType.OPPFOLGINGSPLAN_OPPRETTET

    override suspend fun process(
        connection: Connection,
        message: OutboxMessage,
    ): OutboxResult {
        val oppfolgingsplanUuid = try {
            UUID.fromString(message.externalRef)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Outbox message externalRef is not a valid UUID")
        }
        val recipient = connection.findOppfolgingsplanVarselRecipient(oppfolgingsplanUuid)
            ?: return OutboxResult.IRRELEVANT
        val dispatch = Budstikka.brukervarselCreate(
            eventId = EventId(message.uuid),
            reference = oppfolgingsplanUuid.toString(),
            sykmeldt = PersonIdentifier(recipient.sykmeldtFnr),
            varseltype = Varseltype.BESKJED,
            text = OPPFOLGINGSPLAN_CREATED_BUDSTIKKA_TEXT,
            link = oppfolgingsplanUrl,
        )
        publisher.publish(dispatch)
        return OutboxResult.SENT
    }
}
