package no.nav.syfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.dinesykmeldte.Sykmeldt
import no.nav.syfo.oppfolgingsplan.dto.OppfolgingsplanUtkast
import java.time.LocalDate

fun defaultOppfolginsplanUtkast() = OppfolgingsplanUtkast(
    sykmeldtFnr = "12345678901",
    narmesteLederFnr = "10987654321",
    orgnummer = "orgnummer",
    content = ObjectMapper().readValue(
        """
                            {
                                "tittel": "Oppfølgingsplan for Navn Sykmeldt",
                                "innhold": "Dette er en testoppfølgingsplan"
                            }
                            """
    ),
    sluttdato = LocalDate.parse("2020-01-01"),
)

fun defaultSykmeldt() =Sykmeldt(
    "123",
    "orgnummer",
    "12345678901",
    "Navn Sykmeldt",
    true,
)