package no.nav.syfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequest
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*
import no.nav.syfo.dinesykmeldte.Sykmeldt
import no.nav.syfo.oppfolgingsplan.db.PersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.dto.CreateOppfolgingsplanRequest
import no.nav.syfo.oppfolgingsplan.dto.CreateUtkastRequest

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
    sluttdato = LocalDate.now().plus(30, ChronoUnit.DAYS),
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
    sluttdato = LocalDate.now().plus(30, ChronoUnit.DAYS),
    skalDelesMedLege = false,
    skalDelesMedVeileder = false,
)

fun CreateOppfolgingsplanRequest.toPersistedOppfolgingsplan(narmesteLederId: String): PersistedOppfolgingsplan {
    return PersistedOppfolgingsplan(
        sykmeldtFnr = sykmeldtFnr,
        narmesteLederFnr = narmesteLederFnr,
        orgnummer = orgnummer,
        content = content,
        skalDelesMedLege = skalDelesMedLege,
        skalDelesMedVeileder = skalDelesMedLege,
        uuid = UUID.randomUUID(),
        narmesteLederId = narmesteLederId,
        sluttdato = sluttdato,
        createdAt = Instant.now()
    )
}

fun defaultSykmeldt() = Sykmeldt(
    "123",
    "orgnummer",
    "12345678901",
    "Navn Sykmeldt",
    true,
)

val generatedPdfStandin = "whatever".toByteArray(Charsets.UTF_8)

fun mockHttpResponse(message: String, statusCode: HttpStatusCode): HttpResponse {
    val mockHttpResponse = mockk<HttpResponse>()
    val request = mockk<HttpRequest>()

    coEvery { mockHttpResponse.status } returns statusCode
    coEvery { mockHttpResponse.body<String>() } returns message
    coEvery { mockHttpResponse.request } returns request
    return mockHttpResponse
}
