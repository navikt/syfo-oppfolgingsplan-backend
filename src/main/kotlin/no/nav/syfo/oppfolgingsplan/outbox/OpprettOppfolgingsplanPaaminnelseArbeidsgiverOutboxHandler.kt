package no.nav.syfo.oppfolgingsplan.outbox

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.outbox.OutboxResult
import no.nav.syfo.application.outbox.domain.OutboxCancellationReason
import no.nav.syfo.narmesteleder.client.INarmestelederClient
import no.nav.syfo.oppfolgingsplan.db.OpprettOppfolgingsplanPaaminnelseOutboxPayload
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedOpprettOppfolgingsplanPaaminnelse
import no.nav.syfo.oppfolgingsplan.service.OpprettOppfolgingsplanPaaminnelseService
import no.nav.syfo.varsel.budstikka.infrastructure.BudstikkaPublisher
import java.util.UUID

class OpprettOppfolgingsplanPaaminnelseArbeidsgiverOutboxHandler(
    database: DatabaseInterface,
    opprettOppfolgingsplanPaaminnelseService: OpprettOppfolgingsplanPaaminnelseService,
    private val publisher: BudstikkaPublisher,
    private val narmestelederClient: INarmestelederClient,
) : SharedOpprettOppfolgingsplanPaaminnelseOutboxHandler(database, opprettOppfolgingsplanPaaminnelseService) {

    override val messageType = OppfolgingsplanOutboxMessageType.PAAMINNELSE_ARBEIDSGIVER

    override suspend fun publish(
        opprettOppfolgingsplanPaaminnelse: PersistedOpprettOppfolgingsplanPaaminnelse,
        payload: OpprettOppfolgingsplanPaaminnelseOutboxPayload,
        eventId: UUID,
    ): OutboxResult {
        val narmesteleder = narmestelederClient.findActiveNarmesteleder(
            sykmeldtFnr = opprettOppfolgingsplanPaaminnelse.sykmeldtFnr,
            organisasjonsnummer = opprettOppfolgingsplanPaaminnelse.organisasjonsnummer,
        ) ?: return OutboxResult.Cancelled(OutboxCancellationReason.SOURCE_NO_LONGER_ELIGIBLE)

        publisher.publishOpprettOppfolgingsplanPaaminnelse(
            bestillingId = opprettOppfolgingsplanPaaminnelse.bestillingId,
            eventId = eventId,
            orgnummer = opprettOppfolgingsplanPaaminnelse.organisasjonsnummer,
            sykmeldtFnr = opprettOppfolgingsplanPaaminnelse.sykmeldtFnr,
            narmestelederId = narmesteleder.id,
        )
        return OutboxResult.Sent
    }
}
