package no.nav.syfo

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.isSuccess
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.dinesykmeldte.client.DineSykmeldteHttpClient
import no.nav.syfo.dinesykmeldte.client.DineSykmeldteSykmelding
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedOppfolgingsplanUtkast
import no.nav.syfo.oppfolgingsplan.dto.CreateOppfolgingsplanRequest
import no.nav.syfo.oppfolgingsplan.dto.LagreUtkastRequest
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.DateFieldSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.FormSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.RadioGroupFieldOption
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.RadioGroupFieldSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.Section
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.TextFieldSnapshot
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.texas.client.TexasIntrospectionResponse
import no.nav.syfo.texas.client.TexasResponse
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*

fun defaultUtkastMap(): Map<String, String?> = mapOf(
    "typiskArbeidshverdag" to "Dette skrev jeg forrige gang. Kjekt at det blir lagret i et utkast.",
    "arbeidsoppgaverSomKanUtfores" to "df",
    "arbeidsoppgaverSomIkkeKanUtfores" to "df",
    "tidligereTilrettelegging" to "asdf",
    "tilretteleggingFremover" to "qwer",
    "annenTilrettelegging" to "rt",
    "hvordanFolgeOpp" to "df",
    "evalueringsDato" to "2025-11-24T23:00:00.000Z",
    "harDenAnsatteMedvirket" to null,
    "denAnsatteHarIkkeMedvirketBegrunnelse" to ""
)

fun defaultUtkastRequest(mutate: MutableMap<String, String?>.() -> Unit = {}): LagreUtkastRequest =
    LagreUtkastRequest(
        content = defaultUtkastMap().toMutableMap().apply(mutate),
    )

fun defaultOppfolgingsplan() = CreateOppfolgingsplanRequest(
    content = defaultFormSnapshot(),
    evalueringsdato = LocalDate.now().plus(30, ChronoUnit.DAYS),
)

fun defaultPersistedOppfolgingsplan() = PersistedOppfolgingsplan(
    sykmeldtFnr = "12345678901",
    sykmeldtFullName = "Navn Sykmeldt",
    narmesteLederFnr = "10987654321",
    narmesteLederFullName = "Narmesteleder",
    organisasjonsnummer = "orgnummer",
    organisasjonsnavn = "Test AS",
    content = defaultFormSnapshot(),
    skalDelesMedLege = false,
    skalDelesMedVeileder = false,
    uuid = UUID.randomUUID(),
    narmesteLederId = UUID.randomUUID().toString(),
    evalueringsdato = LocalDate.now().plus(30, ChronoUnit.DAYS),
    createdAt = Instant.now()
)

fun defaultPersistedOppfolgingsplanUtkast() = PersistedOppfolgingsplanUtkast(
    uuid = UUID.randomUUID(),
    sykmeldtFnr = "12345678901",
    narmesteLederId = UUID.randomUUID().toString(),
    narmesteLederFnr = "10987654321",
    organisasjonsnummer = "orgnummer",
    content = defaultUtkastMap(),
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


fun defaultFormSnapshot() = FormSnapshot(
    formIdentifier = "oppfolgingsplan",
    formSemanticVersion = "1.0.0",
    formSnapshotVersion = "2.0.0",
    sections = listOf(
        Section(
            sectionId = "arbeidsoppgaver",
            sectionTitle = "Arbeidsoppgaver",
            fields = listOf(
                TextFieldSnapshot(
                    fieldId = "vanligArbeidsdag",
                    value = "Jeg skriver litt om min vanlige arbeidsdag her",
                    label = "Hvordan ser en vanlig arbeidsdag ut?",
                    description = "Beskriv en vanlig arbeidsdag og hvilke oppgaver arbeidstaker gjør på jobben"
                ),
                TextFieldSnapshot(
                    fieldId = "ordinæreArbeidsoppgaver",
                    value = "Jeg skriver litt om mine ordinære arbeidsoppgaver her",
                    label = "Hvilke ordinære arbeidsoppgaver kan forstatt utføres?",
                    description = "Hvilke ordinære arbeidsoppgaver kan forstatt utføres?"
                ),
            )
        ),
        Section(
            sectionId = "tilpasninger",
            sectionTitle = "Tilpasninger",
            fields = listOf(
                RadioGroupFieldSnapshot(
                    fieldId = "arbeidsgiver",
                    label = "Dette er tittelen på en radio gruppe",
                    description = "Dette er en beskrivelse av radio gruppen",
                    options = listOf(
                        RadioGroupFieldOption(
                            optionId = "option1",
                            optionLabel = "Dette er option 1",
                        ),
                        RadioGroupFieldOption(
                            optionId = "option2",
                            optionLabel = "Dette er option 2",
                        ),
                        RadioGroupFieldOption(
                            optionId = "option3",
                            optionLabel = "Dette er option 3",
                        ),
                    ),
                    selectedOptionId = "option2",
                    wasRequired = true
                ),
                DateFieldSnapshot(
                    fieldId = "evalueringsDato",
                    label = "Evalueringsdato",
                    value = LocalDate.now().plusDays(30),
                    description = "Dato for når oppfølgingsplanen skal evalueres",
                    wasRequired = true
                )
            )
        ),
    ),
)

fun TexasHttpClient.defaultMocks(
    pid: String = "userIdentifier",
    acr: String = "Level4",
    navident: String? = null,
    azpName: String = "dev-gcp:some:client",
    clientId: String = "dev-gcp:some:client"
) {
    coEvery { introspectToken(any(), any()) } returns TexasIntrospectionResponse(
        active = true,
        pid = pid,
        acr = acr,
        sub = UUID.randomUUID().toString(),
        NAVident = navident,
        clientId = clientId,
        azpName = azpName,
    )
    coEvery {
        exchangeTokenForDineSykmeldte(any())
    } returns TexasResponse("token", 111, "tokenType")
    coEvery {
        exchangeTokenForIsDialogmelding(any())
    } returns TexasResponse(
        "token",
        111,
        "tokenType"
    )
    coEvery {
        exchangeTokenForIsTilgangskontroll(any())
    } returns TexasResponse(
        "token",
        111,
        "tokenType"
    )
}

fun DineSykmeldteHttpClient.defaultMocks(narmestelederId: String) {
    coEvery {
        getSykmeldtForNarmesteLederId(
            narmestelederId,
            "token"
        )
    } returns defaultSykmeldt().copy(narmestelederId = narmestelederId)
}

fun DineSykmeldteHttpClient.returnsNotFound(narmestelederId: String) {
    coEvery {
        getSykmeldtForNarmesteLederId(
            narmestelederId,
            "token"
        )
    } throws ClientRequestException(
        response = mockk {
            every { status } returns HttpStatusCode.NotFound
            every { call } returns mockk(relaxed = true)
        },
        cachedResponseText = "Not Found"
    )
}

val generatedPdfStandin = "whatever".toByteArray(Charsets.UTF_8)

fun getMockEngine(path: String = "", status: HttpStatusCode, headers: Headers, content: String) =
    MockEngine.Companion { request ->
        when (request.url.fullPath) {
            path -> {
                if (status.isSuccess()) {
                    respond(
                        status = status,
                        headers = headers,
                        content = content.toByteArray(Charsets.UTF_8),
                    )
                } else {
                    respond(
                        status = status,
                        headers = headers,
                        content = content,
                    )
                }
            }

            else -> error("Unhandled request ${request.url.fullPath}")
        }
    }
