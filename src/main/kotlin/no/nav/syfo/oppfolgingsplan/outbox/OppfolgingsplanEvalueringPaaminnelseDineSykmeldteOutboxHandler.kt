package no.nav.syfo.oppfolgingsplan.outbox

import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.syfo.application.outbox.OutboxMessageHandler
import no.nav.syfo.application.outbox.OutboxResult
import no.nav.syfo.application.outbox.domain.OutboxCancellationReason
import no.nav.syfo.application.outbox.domain.OutboxMessage
import no.nav.syfo.oppfolgingsplan.db.OppfolgingsplanEvalueringPaaminnelseRepository
import no.nav.syfo.oppfolgingsplan.db.OppfolgingsplanEvalueringPaaminnelseSource
import no.nav.syfo.util.logger
import no.nav.syfo.varsel.budstikka.infrastructure.BudstikkaPublisher
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class OppfolgingsplanEvalueringPaaminnelseDineSykmeldteOutboxHandler(
    private val repository: OppfolgingsplanEvalueringPaaminnelseRepository,
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
            val source = repository.findOppfolgingsplanEvalueringPaaminnelseSource(
                oppfolgingsplanUuid = oppfolgingsplanUuid,
                clock = Clock.fixed(now, ZoneOffset.UTC),
            )
        ) {
            is OppfolgingsplanEvalueringPaaminnelseSource.Eligible -> {
                val data = source.sourceData
                publisher.publishEvalueringPaaminnelseDineSykmeldte(
                    oppfolgingsplanUuid = oppfolgingsplanUuid,
                    sykmeldtFnr = data.sykmeldtFnr,
                    organisasjonsnummer = data.organisasjonsnummer,
                    organisasjonsnavn = data.organisasjonsnavn,
                    sykmeldtFullName = data.sykmeldtFullName,
                    evalueringsdato = data.evalueringsdato,
                    eventId = message.uuid,
                )
                OutboxResult.Sent
            }

            OppfolgingsplanEvalueringPaaminnelseSource.NoLongerEligible ->
                OutboxResult.Cancelled(OutboxCancellationReason.SOURCE_NO_LONGER_ELIGIBLE)

            OppfolgingsplanEvalueringPaaminnelseSource.NotFound -> {
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
