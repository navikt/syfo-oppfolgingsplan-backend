package no.nav.syfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.dinesykmeldte.Sykmeldt
import no.nav.syfo.oppfolgingsplan.dto.CreateOppfolgingsplanRequest
import no.nav.syfo.oppfolgingsplan.dto.CreateUtkastRequest
import java.time.LocalDate

fun defaultUtkast() = CreateUtkastRequest(
    sykmeldtFnr = "12345678901",
    narmesteLederFnr = "10987654321",
    orgnummer = "orgnummer",
    content = ObjectMapper().readValue(
        """
        {
            "tittel": "Oppfølgingsplan for Navn Sykmeldt",
            "innhold": "Dette er en testoppfølgingsplan"
        }
    """.trimIndent()
    ),
    sluttdato = LocalDate.parse("2020-01-01"),
)

fun defaultOppfolgingsplan() = CreateOppfolgingsplanRequest(
    sykmeldtFnr = "12345678901",
    narmesteLederFnr = "10987654321",
    orgnummer = "orgnummer",
    content = ObjectMapper().readValue(
        """
        {
            "tittel": "Oppfølgingsplan for Navn Sykmeldt",
            "innhold": "Dette er en testoppfølgingsplan"
        }
        """.trimIndent()
    ),
    sluttdato = LocalDate.parse("2023-10-31"),
    skalDelesMedLege = false,
    skalDelesMedVeileder = false,
)

fun defaultSykmeldt() = Sykmeldt(
    "123",
    "orgnummer",
    "12345678901",
    "Navn Sykmeldt",
    true,
)
