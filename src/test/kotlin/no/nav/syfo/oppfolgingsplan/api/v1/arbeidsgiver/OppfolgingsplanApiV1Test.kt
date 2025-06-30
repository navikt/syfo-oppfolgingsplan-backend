package no.nav.syfo.oppfolgingsplan.api.v1.arbeidsgiver

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
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
import io.mockk.verify
import java.time.LocalDate
import no.nav.syfo.TestDB
import no.nav.syfo.dinesykmeldte.DineSykmeldteHttpClient
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.dinesykmeldte.Sykmeldt
import no.nav.syfo.oppfolgingsplan.api.v1.registerApiV1
import no.nav.syfo.oppfolgingsplan.db.findAllOppfolgingsplanerBy
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanUtkastBy
import no.nav.syfo.oppfolgingsplan.db.upsertOppfolgingsplanUtkast
import no.nav.syfo.oppfolgingsplan.dto.Oppfolgingsplan
import no.nav.syfo.oppfolgingsplan.dto.OppfolgingsplanUtkast
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.plugins.installContentNegotiation
import no.nav.syfo.texas.client.TexasExchangeResponse
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.texas.client.TexasIntrospectionResponse
import no.nav.syfo.varsel.EsyfovarselProducer
import no.nav.syfo.varsel.domain.ArbeidstakerHendelse

class OppfolgingsplanApiV1Test : DescribeSpec({

    val texasClientMock = mockk<TexasHttpClient>()
    val dineSykmeldteHttpClientMock = mockk<DineSykmeldteHttpClient>()
    val esyfovarselProducerMock = mockk<EsyfovarselProducer>()
    val testDb = TestDB.database

    val sykemeldtFnr = "12345678901"
    val narmesteLederFnr = "10987654321"
    val orgnummer = "987654321"

    beforeTest {
        clearAllMocks()
        TestDB.clearAllData()
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
                        ),
                        esyfovarselProducer = esyfovarselProducerMock,
                    )
                }
            }
            fn(this)
        }
    }


    describe("Oppfolgingsplan API") {
        it("GET /oppfolgingsplaner should respond with Unauthorized when no authentication is provided") {
            withTestApplication {
                // Act
                val response = client.get("/api/v1/arbeidsgiver/123/oppfolgingsplaner")
                // Assert
                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }
        it("GET /oppfolgingsplaner should respond with Unauthorized when no bearer token is provided") {
            withTestApplication {
                // Act
                val response = client.get {
                    url("/api/v1/arbeidsgiver/123/oppfolgingsplaner")
                    bearerAuth( "")
                }
                // Assert
                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }
        it("GET /oppfolgingsplaner should respond with OK when texas client gives active response") {
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
                } returns Sykmeldt(
                    "123",
                    "orgnummer",
                    "12345678901",
                    "Navn Sykmeldt",
                    true,
                )
                coEvery {
                    esyfovarselProducerMock.sendVarselToEsyfovarsel(any())
                } returns Unit

                // Act
                val response = client.get {
                    url("/api/v1/arbeidsgiver/123/oppfolgingsplaner")
                    bearerAuth("Bearer token")
                }
                // Assert
                response.status shouldBe HttpStatusCode.OK
            }
        }
        it("GET /oppfolgingsplaner should respond with Forbidden when texas acr claim is not Level4") {
            withTestApplication {
                // Arrange
                coEvery {
                    texasClientMock.introspectToken(any(), any())
                } returns TexasIntrospectionResponse(active = true, pid = "userIdentifier", acr = "Level3")


                // Act
                val response = client.get {
                    url("api/v1/arbeidsgiver/123/oppfolgingsplaner")
                    bearerAuth("Bearer token")
                }
                // Assert
                response.status shouldBe HttpStatusCode.Forbidden
            }
        }
        it("GET /oppfolgingsplaner should respond with Unauthorized when texas client gives inactive response") {
            withTestApplication {
                // Arrange
                coEvery {
                    texasClientMock.introspectToken(any(), any())
                } returns TexasIntrospectionResponse(active = false, sub = "user")

                // Act
                val response = client.get {
                    url("/api/v1/arbeidsgiver/123/oppfolgingsplaner")
                    bearerAuth("Bearer token")
                }
                // Assert
                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }
        it("POST /oppfolgingsplaner should respond with 201 when oppfolgingsplan is created successfully") {
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
                } returns Sykmeldt(
                    "123",
                    "orgnummer",
                    "12345678901",
                    "Navn Sykmeldt",
                    true,
                )

                coEvery {
                    esyfovarselProducerMock.sendVarselToEsyfovarsel(any())
                } returns Unit

                // Act
                val response = client.post {
                    url("/api/v1/arbeidsgiver/123/oppfolgingsplaner")
                    bearerAuth("Bearer token")
                    contentType(ContentType.Application.Json)
                    setBody(
                        Oppfolgingsplan(
                            sykmeldtFnr = "12345678901",
                            narmesteLederFnr = "10987654321",
                            orgnummer = "987654321",
                            content = ObjectMapper().readValue(
                                """
                            {
                                "tittel": "Oppfølgingsplan for Navn Sykmeldt",
                                "innhold": "Dette er en testoppfølgingsplan"
                            }
                            """
                            ),
                            sluttdato = LocalDate.parse("2023-10-31"),
                            skalDelesMedLege = false,
                            skalDelesMedVeileder = false,
                        )
                    )
                }
                // Assert
                response.status shouldBe HttpStatusCode.Created

                val persisted = testDb.findAllOppfolgingsplanerBy("123")
                persisted.size shouldBe 1
                persisted.first().sykmeldtFnr shouldBe sykemeldtFnr
                    persisted.first().narmesteLederFnr shouldBe narmesteLederFnr
                persisted.first().narmesteLederId shouldBe "123"
                persisted.first().orgnummer shouldBe orgnummer
                    persisted.first().content.toString() shouldBe
                    """{"tittel":"Oppfølgingsplan for Navn Sykmeldt","innhold":"Dette er en testoppfølgingsplan"}"""
                persisted.first().sluttdato.toString() shouldBe "2023-10-31"
                persisted.first().skalDelesMedLege shouldBe false
                persisted.first().skalDelesMedVeileder shouldBe false
                persisted.first().deltMedVeilederTidspunkt shouldBe null
                persisted.first().deltMedLegeTidspunkt shouldBe null
                verify(exactly = 1) {
                    esyfovarselProducerMock.sendVarselToEsyfovarsel(withArg {
                        val hendelse = it as ArbeidstakerHendelse
                        hendelse.arbeidstakerFnr shouldBe sykemeldtFnr
                        hendelse.orgnummer shouldBe orgnummer
                    })
                }
            }
        }
        it("POST /oppfolgingsplaner creates new oppfolgingsplan and deletes existing utkast for narmesteLederId") {
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
                } returns Sykmeldt(
                    "123",
                    "orgnummer",
                    "12345678901",
                    "Navn Sykmeldt",
                    true,
                )
                coEvery {
                    esyfovarselProducerMock.sendVarselToEsyfovarsel(any())
                } returns Unit

                testDb.upsertOppfolgingsplanUtkast(
                    "123",
                    OppfolgingsplanUtkast(
                        sykmeldtFnr = sykemeldtFnr,
                        narmesteLederFnr = narmesteLederFnr,
                        orgnummer = orgnummer,
                        content = ObjectMapper().readValue("{}"),
                        sluttdato = LocalDate.parse("2023-10-31"),
                    )
                )

                // Act
                client.post {
                    url("/api/v1/arbeidsgiver/123/oppfolgingsplaner")
                    bearerAuth("Bearer token")
                    contentType(ContentType.Application.Json)
                    setBody(
                        Oppfolgingsplan(
                            sykmeldtFnr = "12345678901",
                            narmesteLederFnr = "10987654321",
                            orgnummer = "987654321",
                            content = ObjectMapper().readValue("{}"),
                            sluttdato = LocalDate.parse("2023-10-31"),
                            skalDelesMedLege = false,
                            skalDelesMedVeileder = false,
                        )
                    )
                }
                // Assert
                val persistedOppfolgingsplaner = testDb.findAllOppfolgingsplanerBy("123")
                persistedOppfolgingsplaner.size shouldBe 1

                val persistedUtkast = testDb.findOppfolgingsplanUtkastBy("123")
                persistedUtkast shouldBe null
                verify(exactly = 1) {
                    esyfovarselProducerMock.sendVarselToEsyfovarsel(withArg {
                        val hendelse = it as ArbeidstakerHendelse
                        hendelse.arbeidstakerFnr shouldBe sykemeldtFnr
                        hendelse.orgnummer shouldBe orgnummer
                    })
                }

            }
        }
    }
})
