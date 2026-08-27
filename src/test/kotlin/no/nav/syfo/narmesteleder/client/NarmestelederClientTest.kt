package no.nav.syfo.narmesteleder.client

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.ktor.http.HttpHeaders
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.texas.client.TexasResponse
import no.nav.syfo.util.httpClientDefault
import java.util.UUID

class NarmestelederClientTest :
    DescribeSpec({
        val texasHttpClient = mockk<TexasHttpClient>()
        val wireMockServer = WireMockServer(options().dynamicPort())

        fun client() = NarmestelederClient(
            httpClient = httpClientDefault(),
            narmestelederBaseUrl = wireMockServer.baseUrl(),
            texasHttpClient = texasHttpClient,
            scope = "api://dev-gcp.team-esyfo.esyfo-narmesteleder/.default",
        )

        beforeTest {
            clearAllMocks(currentThreadOnly = true)
            wireMockServer.start()
            coEvery {
                texasHttpClient.systemToken("azuread", "api://dev-gcp.team-esyfo.esyfo-narmesteleder/.default")
            } returns TexasResponse(
                accessToken = "token",
                expiresIn = 3600,
                tokenType = "Bearer",
            )
        }

        afterTest {
            wireMockServer.resetAll()
            wireMockServer.stop()
        }

        it("looks up an active narmesteleder with a system token") {
            wireMockServer.stubFor(
                post(urlPathEqualTo(NARMESTELEDER_LOOKUP_PATH))
                    .withHeader(HttpHeaders.Authorization, equalTo("Bearer token"))
                    .withRequestBody(
                        equalToJson(
                            """
                            {"employeeNationalIdentificationNumber":"12345678901","organizationNumber":"123456789"}
                            """.trimIndent(),
                        ),
                    )
                    .willReturn(
                        aResponse()
                            .withHeader(HttpHeaders.ContentType, "application/json")
                            .withBody(
                                """
                                {
                                  "lineManager": {
                                    "id": "c8d10801-a0cc-4d94-a9ab-0088e850d4f4",
                                    "nationalIdentificationNumber": "10987654321",
                                    "emailAddresses": ["leder@eksempel.no"]
                                  }
                                }
                                """.trimIndent(),
                            ),
                    ),
            )

            val narmesteleder = client().findActiveNarmesteleder("12345678901", "123456789")

            narmesteleder?.id shouldBe UUID.fromString("c8d10801-a0cc-4d94-a9ab-0088e850d4f4")
            narmesteleder?.nationalIdentificationNumber shouldBe "10987654321"
            narmesteleder?.emailAddresses shouldBe listOf("leder@eksempel.no")
            coVerify(exactly = 1) {
                texasHttpClient.systemToken("azuread", "api://dev-gcp.team-esyfo.esyfo-narmesteleder/.default")
            }
        }

        it("returns null when no active narmesteleder exists") {
            wireMockServer.stubFor(
                post(urlPathEqualTo(NARMESTELEDER_LOOKUP_PATH))
                    .willReturn(
                        aResponse()
                            .withHeader(HttpHeaders.ContentType, "application/json")
                            .withBody("""{"lineManager":null}"""),
                    ),
            )

            client().findActiveNarmesteleder("12345678901", "123456789").shouldBeNull()
        }

        it("does not expose response data when the response is malformed") {
            val responseBody = """{"lineManager":{"nationalIdentificationNumber":"10987654321","emailAddresses":["leder@eksempel.no"]"""
            wireMockServer.stubFor(
                post(urlPathEqualTo(NARMESTELEDER_LOOKUP_PATH))
                    .willReturn(
                        aResponse()
                            .withHeader(HttpHeaders.ContentType, "application/json")
                            .withBody(responseBody),
                    ),
            )

            val exception = shouldThrow<IllegalStateException> {
                client().findActiveNarmesteleder("12345678901", "123456789")
            }

            exception.message shouldBe "Narmesteleder returned an invalid response"
            exception.message.orEmpty() shouldNotContain "10987654321"
            exception.message.orEmpty() shouldNotContain "leder@eksempel.no"
            exception.message.orEmpty() shouldNotContain responseBody
            exception.cause.shouldBeNull()
        }

        it("does not expose response data when a response field has an invalid format") {
            val responseBody =
                """
                {
                  "lineManager": {
                    "id": "not-a-uuid",
                    "nationalIdentificationNumber": "10987654321",
                    "emailAddresses": ["leder@eksempel.no"]
                  }
                }
                """.trimIndent()
            wireMockServer.stubFor(
                post(urlPathEqualTo(NARMESTELEDER_LOOKUP_PATH))
                    .willReturn(
                        aResponse()
                            .withHeader(HttpHeaders.ContentType, "application/json")
                            .withBody(responseBody),
                    ),
            )

            val exception = shouldThrow<IllegalStateException> {
                client().findActiveNarmesteleder("12345678901", "123456789")
            }

            exception.message shouldBe "Narmesteleder returned an invalid response"
            exception.message.orEmpty() shouldNotContain "10987654321"
            exception.message.orEmpty() shouldNotContain "leder@eksempel.no"
            exception.message.orEmpty() shouldNotContain responseBody
            exception.cause.shouldBeNull()
        }
    })
