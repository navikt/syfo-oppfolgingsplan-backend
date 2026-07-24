package no.nav.syfo.varsel.budstikka.infrastructure

import java.util.UUID

interface BudstikkaPublisher {
    fun publishOppfolgingsplanCreated(
        oppfolgingsplanUuid: UUID,
        sykmeldtFnr: String,
        eventId: UUID
    ) : UUID
}
