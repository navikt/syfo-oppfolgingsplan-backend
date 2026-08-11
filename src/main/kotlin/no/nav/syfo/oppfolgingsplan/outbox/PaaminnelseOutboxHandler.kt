package no.nav.syfo.oppfolgingsplan.outbox

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.application.outbox.OutboxMessageHandler
import no.nav.syfo.application.outbox.domain.OutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import no.nav.syfo.application.outbox.domain.OutboxRelevans
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedPaaminnelse
import no.nav.syfo.oppfolgingsplan.db.existsOppfolgingsplanCreatedAfterForlopStart
import no.nav.syfo.oppfolgingsplan.db.findPaaminnelseBy
import no.nav.syfo.oppfolgingsplan.model.Paaminnelse
import no.nav.syfo.sykmelding.db.findEarliestFom
import no.nav.syfo.util.configuredJacksonMapper
import no.nav.syfo.util.logger
import java.sql.Connection
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class PaaminnelsePayload(
    val messageType: OutboxMessageType = OutboxMessageType.PAAMINNELSE_OPPFOLGINGSPLAN,
    val forlopFom: LocalDate,
)

class PaaminnelseOutboxHandler(
    private val clock: Clock = Clock.system(ZoneId.of("Europe/Oslo")),
) : OutboxMessageHandler {
    private val log = logger()

    override val messageType: OutboxMessageType = OutboxMessageType.PAAMINNELSE_OPPFOLGINGSPLAN

    override fun evaluateRelevance(connection: Connection, message: OutboxMessage, now: Instant): List<OutboxRelevans> {
        val forlopFom = configuredJacksonMapper.readValue<PaaminnelsePayload>(message.payload).forlopFom
        val paaminnelse = connection.findPaaminnelseBy(UUID.fromString(message.externalRef))
        val earliestFom = paaminnelse?.let { connection.findEarliestFom(
            sykmeldtFnr = paaminnelse.sykmeldtFnr,
            organisasjonsnummer = paaminnelse.organisasjonsnummer,
            today = LocalDate.ofInstant(now, clock.zone),
        )}
        val oppfolgingsplanCreatedAfterForlopStart = paaminnelse?.let { connection.existsOppfolgingsplanCreatedAfterForlopStart(
            sykmeldtFnr = paaminnelse.sykmeldtFnr,
            organisasjonsnummer = paaminnelse.organisasjonsnummer,
            forlopStart = forlopFom.atStartOfDay(clock.zone).toInstant(),
        )} ?: false


/*        return if (
            connection.existsOppfolgingsplanCreatedAfterForlopStart(
                sykmeldtFnr = paaminnelse.sykmeldtFnr,
                organisasjonsnummer = paaminnelse.organisasjonsnummer,
                forlopStart = forlopFom.atStartOfDay(clock.zone).toInstant(),
            )
        ) {
            OutboxRelevans.IkkeRelevant
        } else {
            OutboxRelevans.Relevant
        }*/
        return listOf(paaminnelseIsBestilt(paaminnelse))
    }

    override fun send(connection: Connection, message: OutboxMessage) {
        log.info("Påminnelse-outbox payload={}", message.payload)
    }

    fun paaminnelseIsBestilt(paaminnelse: PersistedPaaminnelse?): OutboxRelevans {
        return when (paaminnelse?.bestilt) {
            true -> OutboxRelevans.Relevant
            else -> OutboxRelevans.IkkeRelevant
        }
    }
    fun paaminnelseForlopErGyldig(paaminnelse: PersistedPaaminnelse?, forlopFom: LocalDate): OutboxRelevans {
        return when (paaminnelse?.forlopFom == forlopFom) {
            true -> OutboxRelevans.Relevant
            false -> OutboxRelevans.IkkeRelevant
        }
    }
    fun forlopFomErLikPaaminnelseFom(paaminnelse: PersistedPaaminnelse?, forlopFom: LocalDate): OutboxRelevans {
        return when (paaminnelse?.forlopFom == forlopFom) {
            true -> OutboxRelevans.Relevant
            false -> OutboxRelevans.IkkeRelevant
        }
    }
    fun
}
