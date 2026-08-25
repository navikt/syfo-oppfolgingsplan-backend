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

    suspend fun publishMinSideArbeidsgiverEvalueringspaaminnelse(
        oppfolgingsplanUuid: UUID,
        sykmeldtFnr: String,
        organisasjonsnummer: String,
        eventId: UUID,
    )

    suspend fun publishPaaminnelse(
        paaminnelseUuid: UUID,
        sykmeldtFnr: String,
        orgnummer: String,
        eventId: UUID,
        narmestelederId: String,
    )

    suspend fun publishPaaminnelseToDineSykmeldte(
        paaminnelseUuid: UUID,
        sykmeldtFnr: String,
        orgnummer: String,
        eventId: UUID,
        narmestelederId: String,
    )
}
