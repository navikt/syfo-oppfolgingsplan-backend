package no.nav.syfo.oppfolgingsplan.api.v1.sykmeldt

import no.nav.syfo.isdialogmelding.IsDialogmeldingClient
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*
import no.nav.syfo.TestDB
import no.nav.syfo.defaultPersistedOppfolgingsplan
import no.nav.syfo.defaultPersistedOppfolgingsplanUtkast
import no.nav.syfo.dinesykmeldte.DineSykmeldteHttpClient
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.generatedPdfStandin
import no.nav.syfo.isdialogmelding.IsDialogmeldingService
import no.nav.syfo.oppfolgingsplan.api.v1.registerApiV1
import no.nav.syfo.oppfolgingsplan.db.PersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.dto.SykmeldtOppfolgingsplanOverview
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.pdfgen.PdfGenService
import no.nav.syfo.persistOppfolgingsplan
import no.nav.syfo.persistOppfolgingsplanUtkast
import no.nav.syfo.plugins.installContentNegotiation
import no.nav.syfo.texas.client.TexasExchangeResponse
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.texas.client.TexasIntrospectionResponse
import no.nav.syfo.varsel.EsyfovarselProducer

class OppfolgingsplanApiV1Test : DescribeSpec({

    val texasClientMock = mockk<TexasHttpClient>()
    val dineSykmeldteHttpClientMock = mockk<DineSykmeldteHttpClient>()
    val esyfovarselProducerMock = mockk<EsyfovarselProducer>()
    val testDb = TestDB.database
    val narmestelederId = UUID.randomUUID().toString()
    val pdfGenService = mockk<PdfGenService>()
    val isDialogmeldingClientMock = mockk<IsDialogmeldingClient>()

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
                            esyfovarselProducer = esyfovarselProducerMock
                        ),
                        pdfGenService = pdfGenService,
                        isDialogmeldingService = IsDialogmeldingService(isDialogmeldingClientMock)
                    )
                }
            }
            fn(this)
        }
    }

    describe("Oppfolgingsplan API") {
        describe("Oversikt") {
            it("GET /oppfolgingsplaner/oversikt should respond with Unauthorized when no authentication is provided") {
                withTestApplication {
                    // Act
                    val response = client.get("/api/v1/sykmeldt/oppfolgingsplaner/oversikt")

                    // Assert
                    response.status shouldBe HttpStatusCode.Unauthorized
                }
            }
            it("GET /oppfolgingsplaner/oversikt should respond with Unauthorized when no bearer token is provided") {
                withTestApplication {
                    // Act
                    val response = client.get {
                        url("/api/v1/sykmeldt/oppfolgingsplaner/oversikt")
                        bearerAuth("")
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.Unauthorized
                }
            }
            it("GET /oppfolgingsplaner/oversikt should respond with OK when texas client gives active response") {
                withTestApplication {
                    // Arrange
                    coEvery {
                        texasClientMock.introspectToken(any(), any())
                    } returns TexasIntrospectionResponse(active = true, pid = "userIdentifier", acr = "Level4")

                    coEvery {
                        texasClientMock.exchangeTokenForDineSykmeldte(any())
                    } returns TexasExchangeResponse("token", 111, "tokenType")

                    // Act
                    val response = client.get {
                        url("/api/v1/sykmeldt/oppfolgingsplaner/oversikt")
                        bearerAuth("Bearer token")
                    }
                    // Assert
                    response.status shouldBe HttpStatusCode.OK
                }
            }
            it("GET /oppfolgingsplaner/oversikt should respond with Forbidden when texas acr claim is not Level4") {
                withTestApplication {
                    // Arrange
                    coEvery {
                        texasClientMock.introspectToken(any(), any())
                    } returns TexasIntrospectionResponse(active = true, pid = "userIdentifier", acr = "Level3")

                    // Act
                    val response = client.get {
                        url("api/v1/sykmeldt/oppfolgingsplaner/oversikt")
                        bearerAuth("Bearer token")
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.Forbidden
                }
            }
            it("GET /oppfolgingsplaner/oversikt should respond with Unauthorized when texas client gives inactive response") {
                withTestApplication {
                    // Arrange
                    coEvery {
                        texasClientMock.introspectToken(any(), any())
                    } returns TexasIntrospectionResponse(active = false, sub = "user")

                    // Act
                    val response = client.get {
                        url("/api/v1/sykmeldt/oppfolgingsplaner/oversikt")
                        bearerAuth("Bearer token")
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.Unauthorized
                }
            }
            it("GET /oppfolgingsplaner/oversikt should respond with OK and return overview") {
                val sykmeldtFnr = "12345678901"
                val narmestelederFnr = "10987654321"
                withTestApplication {
                    // Arrange
                    coEvery {
                        texasClientMock.introspectToken(
                            any(),
                            any()
                        )
                    } returns TexasIntrospectionResponse(
                        active = true,
                        pid = sykmeldtFnr,
                        acr = "Level4"
                    )

                    coEvery { texasClientMock.exchangeTokenForDineSykmeldte(any()) } returns TexasExchangeResponse(
                        "token",
                        111,
                        "tokenType"
                    )

                    val firstPlanUUID = testDb.persistOppfolgingsplan(
                        defaultPersistedOppfolgingsplan()
                            .copy(
                                narmesteLederId = narmestelederId,
                                sluttdato = LocalDate.now().minus(45, ChronoUnit.DAYS)
                            )
                    )
                    val latestPlanUUID = testDb.persistOppfolgingsplan(
                        defaultPersistedOppfolgingsplan()
                            .copy(narmesteLederId = narmestelederId)
                    )
                    testDb.persistOppfolgingsplanUtkast(
                        defaultPersistedOppfolgingsplanUtkast()
                            .copy(
                                narmesteLederId = narmestelederId,
                                narmesteLederFnr = narmestelederFnr,
                                sykmeldtFnr = sykmeldtFnr,
                            )
                    )

                    // Act
                    val response = client.get {
                        url("/api/v1/sykmeldt/oppfolgingsplaner/oversikt")
                        bearerAuth("Bearer token")
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.OK
                    val overview = response.body<SykmeldtOppfolgingsplanOverview>()
                    overview.oppfolgingsplaner.firstOrNull()?.uuid shouldBe latestPlanUUID
                    overview.previousOppfolgingsplaner.size shouldBe 1
                    overview.previousOppfolgingsplaner.first().uuid shouldBe firstPlanUUID
                }
            }
        }
        it("GET /oppfolgingsplaner/{uuid} should respond with NotFound if oppfolgingsplan does not exist") {
            withTestApplication {
                // Arrange
                coEvery {
                    texasClientMock.introspectToken(
                        any(),
                        any()
                    )
                } returns TexasIntrospectionResponse(active = true, pid = "userIdentifier", acr = "Level4")
                coEvery { texasClientMock.exchangeTokenForDineSykmeldte(any()) } returns TexasExchangeResponse(
                    "token",
                    111,
                    "tokenType"
                )

                // Act
                val response = client.get {
                    url("/api/v1/sykmeldt/oppfolgingsplaner/00000000-0000-0000-0000-000000000000")
                    bearerAuth("Bearer token")
                }

                // Assert
                response.status shouldBe HttpStatusCode.NotFound
            }
        }
        describe("By uuid") {
            it("GET /oppfolgingsplaner/{uuid} should respond with OK and return oppfolgingsplan when found and authorized") {
                withTestApplication {
                    // Arrange
                    val sykmeldtFnr = "12345678901"
                    coEvery {
                        texasClientMock.introspectToken(
                            any(),
                            any()
                        )
                    } returns TexasIntrospectionResponse(
                        active = true,
                        pid = sykmeldtFnr,
                        acr = "Level4"
                    )

                    coEvery { texasClientMock.exchangeTokenForDineSykmeldte(any()) } returns TexasExchangeResponse(
                        "token",
                        111,
                        "tokenType"
                    )

                    val existingUUID = testDb.persistOppfolgingsplan(
                        defaultPersistedOppfolgingsplan()
                            .copy(narmesteLederId = narmestelederId)
                    )

                    // Act
                    val response = client.get {
                        url("/api/v1/sykmeldt/oppfolgingsplaner/$existingUUID")
                        bearerAuth("Bearer token")
                    }
                    // Assert
                    response.status shouldBe HttpStatusCode.OK
                    val plan = response.body<PersistedOppfolgingsplan>()
                    plan.uuid shouldBe existingUUID
                }
            }

            it("GET /oppfolgingsplaner/{uuid} should respond with Not found when found and plan does not belong to logged in user") {
                withTestApplication {
                    // Arrange
                    val sykmeldtFnr = "12345678901"
                    coEvery {
                        texasClientMock.introspectToken(
                            any(),
                            any()
                        )
                    } returns TexasIntrospectionResponse(
                        active = true,
                        pid = sykmeldtFnr,
                        acr = "Level4"
                    )

                    coEvery { texasClientMock.exchangeTokenForDineSykmeldte(any()) } returns TexasExchangeResponse(
                        "token",
                        111,
                        "tokenType"
                    )

                    val existingUUID = testDb.persistOppfolgingsplan(
                        defaultPersistedOppfolgingsplan()
                            .copy(narmesteLederId = narmestelederId, sykmeldtFnr = "12345678902")
                    )

                    // Act
                    val response = client.get {
                        url("/api/v1/sykmeldt/oppfolgingsplaner/$existingUUID")
                        bearerAuth("Bearer token")
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.NotFound
                }
            }
        }
        describe("PDF") {
            it("GET /oppfolgingsplaner/{uuid}/pdf should respond with NotFound if oppfolgingsplan does not exist") {
                withTestApplication {
                    // Arrange
                    coEvery {
                        texasClientMock.introspectToken(
                            any(),
                            any()
                        )
                    } returns TexasIntrospectionResponse(active = true, pid = "userIdentifier", acr = "Level4")
                    coEvery { texasClientMock.exchangeTokenForDineSykmeldte(any()) } returns TexasExchangeResponse(
                        "token",
                        111,
                        "tokenType"
                    )

                    // Act
                    val response = client.get {
                        url("/api/v1/sykmeldt/oppfolgingsplaner/00000000-0000-0000-0000-000000000000/pdf")
                        bearerAuth("Bearer token")
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.NotFound
                }
            }

            it("GET /oppfolgingsplaner/{uuid}/pdf should respond with OK and return a ByteArray when found and authorized") {
                withTestApplication {
                    // Arrange
                    val sykmeldtFnr = "12345678901"
                    coEvery {
                        texasClientMock.introspectToken(
                            any(),
                            any()
                        )
                    } returns TexasIntrospectionResponse(
                        active = true,
                        pid = sykmeldtFnr,
                        acr = "Level4"
                    )

                    coEvery { texasClientMock.exchangeTokenForDineSykmeldte(any()) } returns TexasExchangeResponse(
                        "token",
                        111,
                        "tokenType"
                    )

                    coEvery { pdfGenService.generatePdf(any()) } returns generatedPdfStandin

                    val existingUUID = testDb.persistOppfolgingsplan(
                        defaultPersistedOppfolgingsplan()
                            .copy(narmesteLederId = narmestelederId)
                    )

                    // Act
                    val response = client.get {
                        url("/api/v1/sykmeldt/oppfolgingsplaner/$existingUUID/pdf")
                        bearerAuth("Bearer token")
                    }
                    // Assert
                    response.status shouldBe HttpStatusCode.OK
                    response.contentType() shouldBe ContentType.Application.Pdf
                    val pdf = response.body<ByteArray>()
                    pdf.toString(Charsets.UTF_8) shouldBeEqual generatedPdfStandin.toString(Charsets.UTF_8)
                }
            }

            it("GET /oppfolgingsplaner/{uuid}/pdf should respond with InternalServerError if pdf generation fails") {
                withTestApplication {
                    // Arrange
                    val sykmeldtFnr = "12345678901"
                    coEvery {
                        texasClientMock.introspectToken(
                            any(),
                            any()
                        )
                    } returns TexasIntrospectionResponse(
                        active = true,
                        pid = sykmeldtFnr,
                        acr = "Level4"
                    )

                    coEvery { texasClientMock.exchangeTokenForDineSykmeldte(any()) } returns TexasExchangeResponse(
                        "token",
                        111,
                        "tokenType"
                    )

                    coEvery { pdfGenService.generatePdf(any()) } throws RuntimeException("Forced")

                    val existingUUID = testDb.persistOppfolgingsplan(
                        defaultPersistedOppfolgingsplan()
                            .copy(narmesteLederId = narmestelederId)
                    )

                    // Act
                    val response = client.get {
                        url("/api/v1/sykmeldt/oppfolgingsplaner/$existingUUID/pdf")
                        bearerAuth("Bearer token")
                    }
                    // Assert
                    response.status shouldBe InternalServerError
                }
            }
        }
    }
})
