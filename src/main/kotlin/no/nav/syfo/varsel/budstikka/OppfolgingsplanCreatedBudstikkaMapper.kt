package no.nav.syfo.varsel.budstikka

import no.nav.syfo.varsel.budstikka.domain.BrukervarselCreate
import no.nav.syfo.varsel.budstikka.domain.Dispatch
import no.nav.syfo.varsel.budstikka.domain.PersonIdentifier
import no.nav.syfo.varsel.budstikka.domain.Varseltype
import java.util.UUID

const val OPPFOLGINGSPLAN_CREATED_BUDSTIKKA_TEXT = "Det er opprettet en oppfølgingsplan."

fun createOppfolgingsplanCreatedDispatch(
    oppfolgingsplanUuid: UUID,
    sykmeldtFnr: String,
): Dispatch = Dispatch(
    eventId = UUID.randomUUID(),
    reference = oppfolgingsplanUuid.toString(),
    content = BrukervarselCreate(
        personIdentifier = PersonIdentifier(sykmeldtFnr),
        varseltype = Varseltype.BESKJED,
        text = OPPFOLGINGSPLAN_CREATED_BUDSTIKKA_TEXT,
        link = null,
    ),
)
