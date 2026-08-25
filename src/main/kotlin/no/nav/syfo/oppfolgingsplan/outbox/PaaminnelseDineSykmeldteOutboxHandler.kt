package no.nav.syfo.oppfolgingsplan.outbox

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.oppfolgingsplan.db.PaaminnelseOutboxPayload
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedPaaminnelse
import no.nav.syfo.varsel.budstikka.infrastructure.BudstikkaPublisher
import java.util.UUID

class PaaminnelseDineSykmeldteOutboxHandler(
    database: DatabaseInterface,
    private val publisher: BudstikkaPublisher,
) : SharedPaaminnelseOutboxHandler(database) {

    override val messageType = OppfolgingsplanOutboxMessageType.PAAMINNELSE_DINE_SYKMELDTE

    override suspend fun publish(
        paaminnelse: PersistedPaaminnelse,
        payload: PaaminnelseOutboxPayload,
        eventId: UUID,
    ) {
        publisher.publishPaaminnelseToDineSykmeldte(
            paaminnelseUuid = paaminnelse.uuid,
            eventId = eventId,
            sykmeldtFnr = paaminnelse.sykmeldtFnr,
            orgnummer = paaminnelse.organisasjonsnummer,
            narmestelederId = payload.narmestelederId,
        )
    }
}
