package no.nav.syfo.dokarkiv.client

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.mockk.InternalPlatformDsl.toStr
import io.mockk.coEvery
import io.mockk.mockk
import java.util.UUID
import kotlin.test.assertFailsWith
import no.nav.syfo.dokarkiv.DokarkivService
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.texas.client.TexasResponse
import no.nav.syfo.util.httpClientDefault

class DokarkivClientTest : DescribeSpec({
    val getJournalpostRequest = {
        JournalpostRequest(
            tittel = "Test Journalpost",
            tema = "SYK",
            journalpostType = "INNGAAENDE",
            bruker = Bruker(id = "12345678901", idType = DokarkivService.FNR_TYPE),
            dokumenter = emptyList(),
            avsenderMottaker = AvsenderMottaker(
                id = UUID.randomUUID().toString(),
                idType = DokarkivService.ID_TYPE_ORGNR,
                navn = "Test Avsender"
            ),
            journalfoerendeEnhet = 1,
            sak = Sak(sakstype = "GENERELL_SAK"),
            kanal = "NAV_NO",
            eksternReferanseId = UUID.randomUUID().toString()
        )
    }

    fun getMockEngine(status: HttpStatusCode, headers: Headers, content: String) = MockEngine { request ->
        val url = DokarkivClient.JOURNALPOST_API_PATH
        when (request.url.fullPath) {
            url -> {
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
                        content = ByteReadChannel(content),
                    )
                }
            }

            else -> error("Unhandled request ${request.url.fullPath}")
        }
    }

    describe("DokarkivClient") {
        it("should return journalpostId after successfull archiving") {
            // Arrange
            val journalpostResponse = JournalpostResponse(
                emptyList(),
                journalpostId = 123,
                journalpostferdigstilt = true,
                journalstatus = "FERDIGSTILT",
                melding = "Journalpost created successfully"
            )
            val mockEngine = getMockEngine(
                HttpStatusCode.OK,
                Headers.build {
                    append("Content-Type", "application/json")
                },
                content = ObjectMapper().writeValueAsString(journalpostResponse),
            )
            val texasHttpClient = mockk<TexasHttpClient>()
            coEvery { texasHttpClient.systemToken(any(), any()) } returns TexasResponse(
                accessToken = "token",
                expiresIn = 3600,
                tokenType = "Bearer"
            )
            val client = DokarkivClient("", texasHttpClient, "testScope", httpClientDefault(HttpClient(mockEngine)))

            val response = client.sendJournalpostRequestToDokarkiv(getJournalpostRequest())
            response shouldBe journalpostResponse.journalpostId.toStr()
        }
    }

    it("should throw exception when archiving failes") {
        // Arrange
        val mockEngine = getMockEngine(
            HttpStatusCode.BadRequest,
            headersOf(),
            content = "Forced Error"
        )
        val texasHttpClient = mockk<TexasHttpClient>()
        coEvery { texasHttpClient.systemToken(any(), any()) } returns TexasResponse(
            accessToken = "token",
            expiresIn = 3600,
            tokenType = "Bearer"
        )
        val client = DokarkivClient("", texasHttpClient, "testScope", httpClientDefault(HttpClient(mockEngine)))

        assertFailsWith<ClientRequestException> {
            client.sendJournalpostRequestToDokarkiv(getJournalpostRequest())
        }
    }


})
