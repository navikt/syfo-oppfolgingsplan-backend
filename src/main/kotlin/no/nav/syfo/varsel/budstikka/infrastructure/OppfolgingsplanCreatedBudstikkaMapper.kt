package no.nav.syfo.varsel.budstikka.infrastructure

import no.nav.syfo.varsel.budstikka.domain.BrukervarselCreate
import no.nav.syfo.varsel.budstikka.domain.Dispatch
import no.nav.syfo.varsel.budstikka.domain.PersonIdentifier
import no.nav.syfo.varsel.budstikka.domain.Varseltype
import java.util.UUID

const val OPPFOLGINGSPLAN_CREATED_BUDSTIKKA_TEXT = "Din arbeidsgiver har laget en oppfølgingsplan for deg"

fun createOppfolgingsplanCreatedDispatch(
    oppfolgingsplanUuid: UUID,
    sykmeldtFnr: String,
    budstikkaOppfolgingsplanSykmeldtUrl: String,
): Dispatch = Dispatch(
    reference = oppfolgingsplanUuid.toString(),
    content = BrukervarselCreate(
        personIdentifier = PersonIdentifier(sykmeldtFnr),
        varseltype = Varseltype.BESKJED,
        text = OPPFOLGINGSPLAN_CREATED_BUDSTIKKA_TEXT,
        link = budstikkaOppfolgingsplanSykmeldtUrl,
    ),
)
