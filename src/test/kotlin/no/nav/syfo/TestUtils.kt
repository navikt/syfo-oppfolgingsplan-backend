package no.nav.syfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.dinesykmeldte.DineSykmeldteSykmelding
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*
import no.nav.syfo.dinesykmeldte.Sykmeldt
import no.nav.syfo.oppfolgingsplan.db.PersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.db.PersistedOppfolgingsplanUtkast
import no.nav.syfo.oppfolgingsplan.dto.CreateOppfolgingsplanRequest
import no.nav.syfo.oppfolgingsplan.dto.CreateUtkastRequest

fun defaultUtkast() = CreateUtkastRequest(
    content = ObjectMapper().readValue(
        """
        {
            "tittel": "Oppfølgingsplan for Navn Sykmeldt",
            "innhold": "Dette er en testoppfølgingsplan"
        }
    """.trimIndent()
    ),
    sluttdato = LocalDate.now().plus(30, ChronoUnit.DAYS),
)

fun defaultOppfolgingsplan() = CreateOppfolgingsplanRequest(
    content = ObjectMapper().readValue(
        """
        {
            "tittel": "Oppfølgingsplan for Navn Sykmeldt",
            "innhold": "Dette er en testoppfølgingsplan"
        }
        """.trimIndent()
    ),
    sluttdato = LocalDate.now().plus(30, ChronoUnit.DAYS),
    skalDelesMedLege = false,
    skalDelesMedVeileder = false,
)

fun defaultPersistedOppfolgingsplan() = PersistedOppfolgingsplan(
    sykmeldtFnr = "12345678901",
    sykmeldtFullName = "Navn Sykmeldt",
    narmesteLederFnr = "10987654321",
    organisasjonsnummer = "orgnummer",
    content = ObjectMapper().readValue(
        """
        {
            "tittel": "Oppfølgingsplan for Navn Sykmeldt",
            "innhold": "Dette er en testoppfølgingsplan"
        }
        """.trimIndent()
    ),
    skalDelesMedLege = false,
    skalDelesMedVeileder = false,
    uuid = UUID.randomUUID(),
    narmesteLederId = UUID.randomUUID().toString(),
    sluttdato = LocalDate.now().plus(30, ChronoUnit.DAYS),
    createdAt = Instant.now()
)

fun defaultPersistedOppfolgingsplanUtkast() = PersistedOppfolgingsplanUtkast(
    uuid = UUID.randomUUID(),
    sykmeldtFnr = "12345678901",
    narmesteLederId = UUID.randomUUID().toString(),
    narmesteLederFnr = "10987654321",
    organisasjonsnummer = "orgnummer",
    content = ObjectMapper().readValue(
        """
        {
            "tittel": "Utkast for Navn Sykmeldt",
            "innhold": "Dette er et testutkast"
        }
        """.trimIndent()
    ),
    sluttdato = LocalDate.now().plus(30, ChronoUnit.DAYS),
    createdAt = Instant.now(),
    updatedAt = Instant.now(),
)

fun defaultSykmeldt() = Sykmeldt(
    "123",
    "orgnummer",
    "12345678901",
    "Navn Sykmeldt",
    sykmeldinger = listOf(DineSykmeldteSykmelding("Test AS")),
    true,
)

val generatedPdfStandin = "whatever".toByteArray(Charsets.UTF_8)
