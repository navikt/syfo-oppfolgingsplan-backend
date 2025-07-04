package no.nav.syfo.oppfolgingsplan.api.v1.arbeidsgiver

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.TestDB
import no.nav.syfo.defaultOppfolginsplanUtkast
import no.nav.syfo.defaultSykmeldt
import no.nav.syfo.dinesykmeldte.DineSykmeldteHttpClient
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.oppfolgingsplan.api.v1.registerApiV1
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanUtkastBy
import no.nav.syfo.oppfolgingsplan.db.upsertOppfolgingsplanUtkast
import no.nav.syfo.oppfolgingsplan.dto.OppfolgingsplanUtkast
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.plugins.installContentNegotiation
import no.nav.syfo.texas.client.TexasExchangeResponse
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.texas.client.TexasIntrospectionResponse
import java.time.LocalDate
import no.nav.syfo.varsel.EsyfovarselProducer

class OppfolgingsplanUtkastApiV1Test : DescribeSpec({

    val texasClientMock = mockk<TexasHttpClient>()
    val dineSykmeldteHttpClientMock = mockk<DineSykmeldteHttpClient>()
    val testDb = TestDB.Companion.database
    val esyfovarselProducerMock = mockk<EsyfovarselProducer>()

    beforeTest {
        clearAllMocks()
        TestDB.Companion.clearAllData()
    }

    fun withTestApplication(
        fn: suspend ApplicationTestBuilder.() -> Unit
    ) {
        testApplication {
            this.client = createClient {
                install(ContentNegotiation) {
                    jackson {
                        registerKotlinModule()
                        registerModule(JavaTimeModule())
                        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    }
                }
            }
            application {
                installContentNegotiation()
                routing {
                    registerApiV1(
                        DineSykmeldteService(dineSykmeldteHttpClientMock),
                        texasClientMock,
                        oppfolgingsplanService = OppfolgingsplanService(
                            database = testDb,
                            esyfovarselProducer = esyfovarselProducerMock,
                        )
                    )
                }
            }
            fn(this)
        }
    }
    describe("Oppfolgingsplan Utkast API V1") {
        it("PUT /oppfolgingsplaner/utkast creates a new draft if it does not exist") {
            withTestApplication {
                // Arrange
                coEvery {
                    texasClientMock.introspectToken(any(), any())
                } returns TexasIntrospectionResponse(active = true, pid = "userIdentifier", acr = "Level4")

                coEvery {
                    texasClientMock.exhangeTokenForDineSykmeldte(any())
                } returns TexasExchangeResponse("token", 111, "tokenType")

                coEvery {
                    dineSykmeldteHttpClientMock.getSykmeldtForNarmesteLederId("123", "token")
                } returns defaultSykmeldt()

                // Act
                val response = client.put("/api/v1/arbeidsgiver/123/oppfolgingsplaner/utkast") {
                    bearerAuth("Bearer token")
                    contentType(ContentType.Application.Json)
                    setBody(defaultOppfolginsplanUtkast())
                }

                // Assert
                response.status shouldBe HttpStatusCode.OK

                val persisted = testDb.findOppfolgingsplanUtkastBy("123")
                persisted shouldNotBe null
                persisted?.let {
                    it.sykmeldtFnr shouldBe "12345678901"
                    it.narmesteLederId shouldBe "123"
                    it.narmesteLederFnr shouldBe "10987654321"
                    it.orgnummer shouldBe "orgnummer"
                    it.content shouldNotBe null
                    it.sluttdato shouldBe LocalDate.parse("2020-01-01")
                }
            }
        }

        it("PUT /oppfolgingsplaner/utkast overwrite existing draft") {
            withTestApplication {
                // Arrange
                coEvery {
                    texasClientMock.introspectToken(any(), any())
                } returns TexasIntrospectionResponse(active = true, pid = "userIdentifier", acr = "Level4")

                coEvery {
                    texasClientMock.exhangeTokenForDineSykmeldte(any())
                } returns TexasExchangeResponse("token", 111, "tokenType")

                coEvery {
                    dineSykmeldteHttpClientMock.getSykmeldtForNarmesteLederId("123", "token")
                } returns defaultSykmeldt()

                val existingUUID = testDb.upsertOppfolgingsplanUtkast(
                    "123", defaultOppfolginsplanUtkast().copy(
                            content = ObjectMapper().readValue(
                                """
                            {
                                "innhold": "Dette er en testoppfølgingsplan"
                            }
                            """.trimIndent()
                            ), sluttdato = LocalDate.parse("2020-01-01")
                        )
                )

                // Act
                val response = client.put("/api/v1/arbeidsgiver/123/oppfolgingsplaner/utkast") {
                    bearerAuth("Bearer token")
                    contentType(ContentType.Application.Json)
                    setBody(
                        defaultOppfolginsplanUtkast().copy(
                                content = ObjectMapper().readValue(
                                    """
                            {
                                "innhold": "Nytt innhold"
                            }
                            """.trimIndent()
                                ), sluttdato = LocalDate.parse("2020-01-02")
                            )
                    )
                }

                // Assert
                response.status shouldBe HttpStatusCode.OK

                val persisted = testDb.findOppfolgingsplanUtkastBy("123")
                persisted shouldNotBe null
                persisted?.let {
                    it.uuid shouldBe existingUUID
                    it.sykmeldtFnr shouldBe "12345678901"
                    it.narmesteLederId shouldBe "123"
                    it.narmesteLederFnr shouldBe "10987654321"
                    it.orgnummer shouldBe "orgnummer"
                    it.content?.get("innhold")?.asText() shouldBe "Nytt innhold"
                    it.sluttdato shouldBe LocalDate.parse("2020-01-02")
                }
            }
        }

        it("GET /oppfolgingsplaner/utkast should retrieve the current oppfolgingsplan utkast") {
            withTestApplication {
                // Arrange
                coEvery {
                    texasClientMock.introspectToken(any(), any())
                } returns TexasIntrospectionResponse(active = true, pid = "userIdentifier", acr = "Level4")

                coEvery {
                    texasClientMock.exhangeTokenForDineSykmeldte(any())
                } returns TexasExchangeResponse("token", 111, "tokenType")

                coEvery {
                    dineSykmeldteHttpClientMock.getSykmeldtForNarmesteLederId("123", "token")
                } returns defaultSykmeldt()

                val existingUUID = testDb.upsertOppfolgingsplanUtkast(
                    "123", defaultOppfolginsplanUtkast()
                )

                // Act
                val res = client.get("/api/v1/arbeidsgiver/123/oppfolgingsplaner/utkast") {
                    bearerAuth("Bearer token")
                }

                // Assert
                res.status shouldBe HttpStatusCode.OK
                val utkast = res.body<OppfolgingsplanUtkast>()
                utkast shouldNotBe null
                utkast.uuid shouldBe existingUUID
                utkast.sykmeldtFnr shouldBe "12345678901"
                utkast.narmesteLederFnr shouldBe "10987654321"
                utkast.orgnummer shouldBe "orgnummer"
                utkast.content?.get("innhold")?.asText() shouldBe "Dette er en testoppfølgingsplan"
                utkast.sluttdato shouldBe LocalDate.parse("2020-01-01")
            }
        }
    }
})
