package no.nav.syfo.oppfolgingsplan.db.domain

import java.util.UUID

class PersistedBudstikkaVarsel(
    val oppfolgingsplanUuid: UUID,
    val sykmeldtFnr: String,
    val eventId: UUID,
) {
    override fun toString(): String = "PersistedBudstikkaVarsel(oppfolgingsplanUuid=$oppfolgingsplanUuid, eventId=$eventId)"
}
