package no.nav.syfo.oppfolgingsplan.dto

import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.FormSnapshot
import java.time.Instant
import java.time.LocalDate

data class OppfolgingsplanUtkastResponse(
    val canEditPlan: Boolean,
    val sykmeldtFnr: String,
    val narmesteLederId: String,
    val narmesteLederFnr: String,
    val organisasjonsnummer: String,
    val content: FormSnapshot?,
    val evalueringsdato: LocalDate?,
    val createdAt: Instant,
    val sistLagret: Instant,
)

data class CreateUtkastRequest(
    val content: FormSnapshot?,
    val evalueringsdato: LocalDate?,
)