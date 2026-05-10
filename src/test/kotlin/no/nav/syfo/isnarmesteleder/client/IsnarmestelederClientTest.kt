package no.nav.syfo.isnarmesteleder.client

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.getMockEngine
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.texas.client.TexasResponse
import no.nav.syfo.util.httpClientDefault

class IsnarmestelederClientTest :
    DescribeSpec({
        describe("IsnarmestelederHttpClient") {
            it("exchanges TokenX token and returns nærmeste leder-relasjoner") {
                val texasClient = mockk<TexasHttpClient>()
                coEvery {
                    texasClient.exchangeTokenForIsnarmesteleder("user-token")
                } returns TexasResponse(
                    accessToken = "exchanged-token",
                    expiresIn = 3600,
                    tokenType = "Bearer",
                )

                val mockEngine = getMockEngine(
                    path = IsnarmestelederHttpClient.NARMESTELEDER_RELASJONER_PATH,
                    status = HttpStatusCode.OK,
                    headers = Headers.build {
                        append("Content-Type", "application/json")
                    },
                    content = """
                        [
                          {
                            "uuid": "11111111-1111-1111-1111-111111111111",
                            "virksomhetsnummer": "999888777",
                            "virksomhetsnavn": "Testbedrift AS",
                            "narmesteLederPersonIdentNumber": "12345678910",
                            "narmesteLederNavn": "Test Testesen",
                            "status": "INNMELDT_AKTIV",
                            "aktivFom": "2024-01-01",
                            "aktivTom": null
                          }
                        ]
                    """.trimIndent(),
                )

                val client = IsnarmestelederHttpClient(
                    httpClient = httpClientDefault(HttpClient(mockEngine)),
                    texasHttpClient = texasClient,
                    isnarmestelederBaseUrl = "",
                )

                val result = client.getNarmesteLederRelasjoner("user-token")

                result shouldHaveSize 1
                result.first().virksomhetsnummer shouldBe "999888777"
                coVerify(exactly = 1) { texasClient.exchangeTokenForIsnarmesteleder("user-token") }
            }
        }
    })
