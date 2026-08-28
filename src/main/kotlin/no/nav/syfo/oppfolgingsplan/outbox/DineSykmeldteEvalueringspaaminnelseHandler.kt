package no.nav.syfo.oppfolgingsplan.outbox

import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.syfo.application.outbox.OutboxMessageHandler
import no.nav.syfo.application.outbox.OutboxResult
import no.nav.syfo.application.outbox.domain.OutboxCancellationReason
import no.nav.syfo.application.outbox.domain.OutboxMessage
import no.nav.syfo.oppfolgingsplan.service.EvalueringspaaminnelseEligibility
import no.nav.syfo.oppfolgingsplan.service.EvalueringspaaminnelseEligibilityService
import no.nav.syfo.util.logger
import no.nav.syfo.varsel.budstikka.infrastructure.BudstikkaPublisher
import java.time.Instant
import java.util.UUID

class DineSykmeldteEvalueringspaaminnelseHandler(
    private val eligibilityService: EvalueringspaaminnelseEligibilityService,
    private val publisher: BudstikkaPublisher,
) : OutboxMessageHandler {
    private val log = logger()

    override val messageType = OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_DINE_SYKMELDTE

    override suspend fun handle(
        message: OutboxMessage,
        now: Instant,
    ): OutboxResult {
        val oppfolgingsplanUuid = message.externalRef.toOppfolgingsplanUuid()
        return when (
            val eligibility = eligibilityService.resolve(
                oppfolgingsplanUuid = oppfolgingsplanUuid,
                now = now,
            )
        ) {
            is EvalueringspaaminnelseEligibility.Eligible -> {
                val recipient = eligibility.recipient
                publisher.publishDineSykmeldteEvalueringspaaminnelse(
                    oppfolgingsplanUuid = oppfolgingsplanUuid,
                    sykmeldtFnr = recipient.sykmeldtFnr,
                    organisasjonsnummer = recipient.organisasjonsnummer,
                    eventId = message.uuid,
                )
                OutboxResult.Sent
            }

            EvalueringspaaminnelseEligibility.NoLongerEligible ->
                OutboxResult.Cancelled(OutboxCancellationReason.SOURCE_NO_LONGER_ELIGIBLE)

            EvalueringspaaminnelseEligibility.NotFound -> {
                log.error(
                    "Cancelling evaluation reminder because its source oppfolgingsplan was not found {} {}",
                    kv("message_type", message.messageType.value),
                    kv("cancellation_reason", OutboxCancellationReason.SOURCE_NOT_FOUND.value),
                )
                OutboxResult.Cancelled(OutboxCancellationReason.SOURCE_NOT_FOUND)
            }
        }
    }

    private fun String.toOppfolgingsplanUuid(): UUID = try {
        UUID.fromString(this)
    } catch (error: IllegalArgumentException) {
        throw IllegalArgumentException("Outbox externalRef must be an oppfolgingsplan UUID", error)
    }
}
