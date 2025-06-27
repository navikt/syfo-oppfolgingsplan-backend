package no.nav.syfo.oppfolgingsplan

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
import no.nav.syfo.TestDB
import no.nav.syfo.dinesykmeldte.DineSykmeldteHttpClient
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.dinesykmeldte.Sykmeldt
import no.nav.syfo.oppfolgingsplan.api.v1.registerArbeidsgiverOppfolgingsplanApiV1
import no.nav.syfo.oppfolgingsplan.db.findAllOppfolgingsplanerBy
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanUtkastBy
import no.nav.syfo.oppfolgingsplan.db.upsertOppfolgingsplanUtkast
import no.nav.syfo.oppfolgingsplan.domain.Oppfolgingsplan
import no.nav.syfo.oppfolgingsplan.domain.OppfolgingsplanUtkast
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.plugins.installContentNegotiation
import no.nav.syfo.texas.client.TexasExchangeResponse
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.texas.client.TexasIntrospectionResponse
import java.time.LocalDate

class ArbeidsgiverOppfolgingsplanApiV1Test : DescribeSpec({

    val texasClientMock = mockk<TexasHttpClient>()
    val dineSykmeldteHttpClientMock = mockk<DineSykmeldteHttpClient>()
    val testDb = TestDB.database

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
                    registerArbeidsgiverOppfolgingsplanApiV1(
                        DineSykmeldteService(dineSykmeldteHttpClientMock),
                        texasClientMock,
                        oppfolgingsplanService = OppfolgingsplanService(
                            database = testDb,
                        )
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
                val response = client.get("/arbeidsgiver/123/oppfolgingsplaner")
                // Assert
                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }
        it("GET /oppfolgingsplaner should respond with Unauthorized when no bearer token is provided") {
            withTestApplication {
                // Act
                val response = client.get {
                    url("/arbeidsgiver/123/oppfolgingsplaner")
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

                // Act
                val response = client.get {
                    url("/arbeidsgiver/123/oppfolgingsplaner")
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
                    url("/arbeidsgiver/123/oppfolgingsplaner")
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
                    url("/arbeidsgiver/123/oppfolgingsplaner")
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

                // Act
                val response = client.post {
                    url("/arbeidsgiver/123/oppfolgingsplaner")
                    bearerAuth("Bearer token")
                    contentType(ContentType.Application.Json)
                    setBody(Oppfolgingsplan(
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
                    ))
                }
                // Assert
                response.status shouldBe HttpStatusCode.Created

                val persisted = testDb.findAllOppfolgingsplanerBy("123")
                persisted.size shouldBe 1
                persisted.first().sykmeldtFnr shouldBe "12345678901"
                persisted.first().narmesteLederFnr shouldBe "10987654321"
                persisted.first().narmesteLederId shouldBe "123"
                persisted.first().orgnummer shouldBe "987654321"
                persisted.first().content.toString() shouldBe
                        """{"tittel":"Oppfølgingsplan for Navn Sykmeldt","innhold":"Dette er en testoppfølgingsplan"}"""
                persisted.first().sluttdato.toString() shouldBe "2023-10-31"
                persisted.first().skalDelesMedLege shouldBe false
                persisted.first().skalDelesMedVeileder shouldBe false
                persisted.first().deltMedVeilederTidspunkt shouldBe null
                persisted.first().deltMedLegeTidspunkt shouldBe null
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
                testDb.upsertOppfolgingsplanUtkast(
                    "123",
                    OppfolgingsplanUtkast(
                        sykmeldtFnr = "12345678901",
                        narmesteLederFnr = "10987654321",
                        orgnummer = "987654321",
                        content = ObjectMapper().readValue("{}"),
                        sluttdato = LocalDate.parse("2023-10-31"),
                    )
                )

                // Act
                client.post {
                    url("/arbeidsgiver/123/oppfolgingsplaner")
                    bearerAuth("Bearer token")
                    contentType(ContentType.Application.Json)
                    setBody(Oppfolgingsplan(
                        sykmeldtFnr = "12345678901",
                        narmesteLederFnr = "10987654321",
                        orgnummer = "987654321",
                        content = ObjectMapper().readValue("{}"),
                        sluttdato = LocalDate.parse("2023-10-31"),
                        skalDelesMedLege = false,
                        skalDelesMedVeileder = false,
                    ))
                }
                // Assert
                val persistedOppfolgingsplaner = testDb.findAllOppfolgingsplanerBy("123")
                persistedOppfolgingsplaner.size shouldBe 1

                val persistedUtkast = testDb.findOppfolgingsplanUtkastBy("123")
                persistedUtkast shouldBe null
            }
        }
    }
})