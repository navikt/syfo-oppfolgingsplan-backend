package no.nav.syfo.oppfolgingsplan.outbox

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.outbox.OutboxResult
import no.nav.syfo.oppfolgingsplan.db.OpprettOppfolgingsplanPaaminnelseOutboxPayload
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedOpprettOppfolgingsplanPaaminnelse
import no.nav.syfo.oppfolgingsplan.service.OpprettOppfolgingsplanPaaminnelseService
import no.nav.syfo.varsel.budstikka.infrastructure.BudstikkaPublisher
import java.util.UUID

class OpprettOppfolgingsplanPaaminnelseDineSykmeldteOutboxHandler(
    database: DatabaseInterface,
    opprettOppfolgingsplanPaaminnelseService: OpprettOppfolgingsplanPaaminnelseService,
    private val publisher: BudstikkaPublisher,
) : SharedOpprettOppfolgingsplanPaaminnelseOutboxHandler(database, opprettOppfolgingsplanPaaminnelseService) {

    override val messageType = OppfolgingsplanOutboxMessageType.PAAMINNELSE_DINE_SYKMELDTE

    override suspend fun publish(
        opprettOppfolgingsplanPaaminnelse: PersistedOpprettOppfolgingsplanPaaminnelse,
        payload: OpprettOppfolgingsplanPaaminnelseOutboxPayload,
        eventId: UUID,
    ): OutboxResult {
        publisher.publishOpprettOppfolgingsplanPaaminnelseToDineSykmeldte(
            bestillingId = opprettOppfolgingsplanPaaminnelse.bestillingId,
            eventId = eventId,
            sykmeldtFnr = opprettOppfolgingsplanPaaminnelse.sykmeldtFnr,
            orgnummer = opprettOppfolgingsplanPaaminnelse.organisasjonsnummer,
        )
        return OutboxResult.Sent
    }
}
