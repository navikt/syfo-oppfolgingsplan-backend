package no.nav.syfo.dokumentporten.client

import io.kotest.core.spec.style.DescribeSpec
import io.ktor.client.HttpClient
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.UUID
import no.nav.syfo.getMockEngine
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.texas.client.TexasResponse
import no.nav.syfo.util.httpClientDefault

class DokumentportenClientTest : DescribeSpec({
    val texasClient = mockk<TexasHttpClient>()

    beforeTest { clearAllMocks() }

    describe("DokumentportenClient") {
        // Arrange
        it("acquires system token from Texas") {
            coEvery {
                texasClient.systemToken(
                    identityProvider = eq(DokumentportenClient.IDENTITY_PROVIDER),
                    target = any()
                )
            } returns TexasResponse(
                accessToken = "token",
                expiresIn = 3600,
                tokenType = "Bearer",
            )
        }

        val mockEngine = getMockEngine(
            path = DokumentportenClient.DOKUMENTPORTEN_DOCUMENT_PATH,
            status = HttpStatusCode.OK,
            headers = Headers.build {
                append("Content-Type", "application/json")
            },
            content = "{}",
        )
        val client = DokumentportenClient(
            dokumentportenBaseUrl = "",
            texasHttpClient = texasClient,
            scope = "scope",
            httpClient = httpClientDefault(HttpClient(mockEngine))
        )

        // Act
        client.publishOppfolgingsplan(
            Document(
                documentId = UUID.randomUUID(),
                title = "Test Dialog",
                summary = "This is a test dialog summary",
                content = "Test content".toByteArray(charset("UTF-8")),
                contentType = "application/json",
                type = DocumentType.OPPFOLGINGSPLAN,
                orgNumber = "orgnummer",
                fnr = "12345678901",
                fullName = "Test Testesen",
            )
        )

        // Assert
        coVerify(exactly = 1) { texasClient.systemToken(eq(DokumentportenClient.IDENTITY_PROVIDER), any()) }
    }
})
