package no.nav.syfo.oppfolgingsplan.outbox

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.oppfolgingsplan.db.PaaminnelseOutboxPayload
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedPaaminnelse
import no.nav.syfo.oppfolgingsplan.service.PaaminnelseService
import no.nav.syfo.varsel.budstikka.infrastructure.BudstikkaPublisher
import java.util.UUID

class PaaminnelseArbeidsgiverOutboxHandler(
    database: DatabaseInterface,
    paaminnelseService: PaaminnelseService,
    private val publisher: BudstikkaPublisher,
) : SharedPaaminnelseOutboxHandler(database, paaminnelseService) {

    override val messageType = OppfolgingsplanOutboxMessageType.PAAMINNELSE_ARBEIDSGIVER

    override suspend fun publish(
        paaminnelse: PersistedPaaminnelse,
        payload: PaaminnelseOutboxPayload,
        eventId: UUID,
    ) {
        publisher.publishPaaminnelse(
            paaminnelseUuid = paaminnelse.uuid,
            eventId = eventId,
            orgnummer = paaminnelse.organisasjonsnummer,
            sykmeldtFnr = paaminnelse.sykmeldtFnr,
            narmestelederId = "payload.narmestelederId", // TODO: Få narmestelederId fra et annet sted
        )
    }
}
