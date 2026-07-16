package no.nav.syfo.varsel.budstikka

import java.util.UUID

interface BudstikkaPublisher {
    fun publishOppfolgingsplanCreated(
        oppfolgingsplanUuid: UUID,
        sykmeldtFnr: String,
    )
}

object NoOpBudstikkaPublisher : BudstikkaPublisher {
    override fun publishOppfolgingsplanCreated(
        oppfolgingsplanUuid: UUID,
        sykmeldtFnr: String,
    ) = Unit
}
