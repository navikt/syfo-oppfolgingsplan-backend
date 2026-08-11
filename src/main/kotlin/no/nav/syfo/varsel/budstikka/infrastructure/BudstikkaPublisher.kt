package no.nav.syfo.varsel.budstikka.infrastructure

import no.nav.budstikka.contract.EncodedDispatch

interface BudstikkaPublisher {
    suspend fun publish(dispatch: EncodedDispatch)
}
