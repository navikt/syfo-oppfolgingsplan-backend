package no.nav.syfo.varsel.budstikka.infrastructure

import java.time.LocalDate
import java.util.UUID

interface BudstikkaPublisher {
    suspend fun publishOppfolgingsplanCreated(
        oppfolgingsplanUuid: UUID,
        sykmeldtFnr: String,
        eventId: UUID,
    )

    suspend fun publishEvalueringPaaminnelseDineSykmeldte(
        oppfolgingsplanUuid: UUID,
        sykmeldtFnr: String,
        organisasjonsnummer: String,
        organisasjonsnavn: String?,
        sykmeldtFullName: String,
        evalueringsdato: LocalDate,
        eventId: UUID,
    )
}
