package no.nav.syfo.aareg.client

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpHeaders
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.texas.client.TexasResponse
import no.nav.syfo.util.httpClientDefault

class AaregClientTest :
    DescribeSpec({
        val texasHttpClient = mockk<TexasHttpClient>()
        val wireMockServer = WireMockServer(options().dynamicPort())

        beforeTest {
            clearAllMocks()
            wireMockServer.start()
        }

        afterTest {
            wireMockServer.resetAll()
            wireMockServer.stop()
        }

        describe("getArbeidsforhold") {
            it("requests arbeidsforhold with system token, headers and query params") {
                coEvery {
                    texasHttpClient.systemToken(
                        AaregClient.IDENTITY_PROVIDER,
                        TexasHttpClient.getTarget("scope"),
                    )
                } returns TexasResponse(
                    accessToken = "token",
                    expiresIn = 3600,
                    tokenType = "Bearer",
                )

                wireMockServer.stubFor(
                    get(urlPathEqualTo(AaregClient.ARBEIDSFORHOLD_PATH))
                        .withHeader(HttpHeaders.Authorization, equalTo("Bearer token"))
                        .withHeader(AaregClient.NAV_PERSONIDENT_HEADER, equalTo("12345678901"))
                        .withQueryParam("regelverk", equalTo("A_ORDNINGEN"))
                        .withQueryParam("arbeidsforholdstatus", equalTo("AKTIV,FREMTIDIG"))
                        .withQueryParam("sporingsinformasjon", equalTo("false"))
                        .willReturn(
                            aResponse()
                                .withHeader(HttpHeaders.ContentType, "application/json")
                                .withBody(
                                    """
                                    [
                                      {
                                        "arbeidssted": {
                                          "identer": [
                                            {
                                              "type": "ORGANISASJONSNUMMER",
                                              "ident": "999999999"
                                            }
                                          ]
                                        },
                                        "ansettelsesperiode": {
                                          "sluttdato": null
                                        },
                                        "ansettelsesdetaljer": [
                                          {
                                            "rapporteringsmaaneder": {
                                              "fra": "2024-01-01",
                                              "til": null
                                            },
                                            "yrke": {
                                              "kode": "2512",
                                              "beskrivelse": "Systemutvikler"
                                            },
                                            "avtaltStillingsprosent": 100.00
                                          }
                                        ]
                                      }
                                    ]
                                    """.trimIndent(),
                                ),
                        ),
                )

                val client = AaregClient(
                    httpClient = httpClientDefault(),
                    aaregBaseUrl = wireMockServer.baseUrl(),
                    texasHttpClient = texasHttpClient,
                    scope = "scope",
                )

                val arbeidsforhold = client.getArbeidsforhold("12345678901")

                arbeidsforhold.shouldHaveSize(1)
                arbeidsforhold.first().arbeidssted.identer.first().ident shouldBe "999999999"
                arbeidsforhold.first().ansettelsesdetaljer.first().yrke?.beskrivelse shouldBe "Systemutvikler"
                arbeidsforhold.first().ansettelsesdetaljer.first().avtaltStillingsprosent.toString() shouldBe "100.00"
                coVerify(exactly = 1) {
                    texasHttpClient.systemToken(
                        AaregClient.IDENTITY_PROVIDER,
                        TexasHttpClient.getTarget("scope"),
                    )
                }
            }
        }
    })
