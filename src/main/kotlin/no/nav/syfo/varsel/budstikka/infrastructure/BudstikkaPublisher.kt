package no.nav.syfo.varsel.budstikka.infrastructure

import java.util.UUID

interface BudstikkaPublisher {
    suspend fun publishOppfolgingsplanCreated(
        oppfolgingsplanUuid: UUID,
        sykmeldtFnr: String,
        eventId: UUID,
    )

    suspend fun publishDineSykmeldteEvalueringspaaminnelse(
        oppfolgingsplanUuid: UUID,
        sykmeldtFnr: String,
        organisasjonsnummer: String,
        eventId: UUID,
    )
}
