package no.nav.syfo

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.isSuccess
import io.mockk.coEvery
import no.nav.syfo.dinesykmeldte.client.DineSykmeldteSykmelding
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*
import no.nav.syfo.dinesykmeldte.client.DineSykmeldteHttpClient
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.oppfolgingsplan.db.PersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.db.PersistedOppfolgingsplanUtkast
import no.nav.syfo.oppfolgingsplan.dto.CreateOppfolgingsplanRequest
import no.nav.syfo.oppfolgingsplan.dto.CreateUtkastRequest
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.FormSection
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.FormSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.FormSnapshotFieldOption
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.RadioGroupFieldSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.TextFieldSnapshot
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.texas.client.TexasIntrospectionResponse
import no.nav.syfo.texas.client.TexasResponse

fun defaultUtkast() = CreateUtkastRequest(
    content = defaultFormSnapshot(),
    evalueringsdato = LocalDate.now().plus(30, ChronoUnit.DAYS),
)

fun defaultOppfolgingsplan() = CreateOppfolgingsplanRequest(
    content = defaultFormSnapshot(),
    evalueringsdato = LocalDate.now().plus(30, ChronoUnit.DAYS),
    skalDelesMedLege = false,
    skalDelesMedVeileder = false,
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
    content = defaultFormSnapshot(),
    evalueringsdato = LocalDate.now().plus(30, ChronoUnit.DAYS),
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
        FormSection(
            sectionId = "arbeidsoppgaver",
            sectionTitle = "Arbeidsoppgaver",
        ),
        FormSection(
            sectionId = "tilpassninger",
            sectionTitle = "Tilpassninger",
        ),
    ),
    fieldSnapshots = listOf(
        TextFieldSnapshot(
            fieldId = "vanligArbeidsdag",
            sectionId = "arbeidsoppgaver",
            value = "Jeg skriver litt om min vanlige arbeidsdag her",
            label = "Hvordan ser en vanlig arbeidsdag ut?",
            description = "Beskriv en vanlig arbeidsdag og hvilke oppgaver arbeidstaker gjør på jobben"
        ),
        TextFieldSnapshot(
            fieldId = "ordinæreArbeidsoppgaver",
            sectionId = "arbeidsoppgaver",
            value = "Jeg skriver litt om mine ordinære arbeidsoppgaver her",
            label = "Hvilke ordinære arbeidsoppgaver kan forstatt utføres?",
            description = "Hvilke ordinære arbeidsoppgaver kan forstatt utføres?"
        ),
        RadioGroupFieldSnapshot(
            fieldId = "arbeidsgiver",
            sectionId = "tilpassninger",
            label = "Dette er tittelen på en radio gruppe",
            description = "Dette er en beskrivelse av radio gruppen",
            options = listOf(
                FormSnapshotFieldOption(
                    optionId = "option1",
                    optionLabel = "Dette er option 1",
                    wasSelected = false
                ),
                FormSnapshotFieldOption(
                    optionId = "option2",
                    optionLabel = "Dette er option 2",
                    wasSelected = true
                ),
                FormSnapshotFieldOption(
                    optionId = "option3",
                    optionLabel = "Dette er option 3",
                    wasSelected = false
                ),
            ),
            wasRequired = true
        )
    )
)

fun TexasHttpClient.defaultMocks(pid: String = "userIdentifier", acr: String = "Level4", navident: String? = null) {
    coEvery { introspectToken(any(), any()) } returns TexasIntrospectionResponse(
        active = true,
        pid = pid,
        acr = acr,
        sub = UUID.randomUUID().toString(),
        NAVident = navident
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
