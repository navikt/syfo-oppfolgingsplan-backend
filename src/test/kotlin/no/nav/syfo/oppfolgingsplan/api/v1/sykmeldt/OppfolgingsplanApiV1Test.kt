package no.nav.syfo.oppfolgingsplan.api.v1.sykmeldt

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.TestDB
import no.nav.syfo.aareg.AaregService
import no.nav.syfo.application.Environment
import no.nav.syfo.application.LocalEnvironment
import no.nav.syfo.application.exception.ApiErrorException
import no.nav.syfo.application.valkey.ValkeyCache
import no.nav.syfo.defaultMocks
import no.nav.syfo.defaultPersistedOppfolgingsplan
import no.nav.syfo.defaultPersistedOppfolgingsplanUtkast
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.dinesykmeldte.client.DineSykmeldteHttpClient
import no.nav.syfo.dokarkiv.DokarkivService
import no.nav.syfo.foresporsel.ForesporselService
import no.nav.syfo.foresporsel.domain.ForesporselStatus
import no.nav.syfo.foresporsel.domain.SykmeldtArbeidsforhold
import no.nav.syfo.generatedPdfStandin
import no.nav.syfo.isdialogmelding.IsDialogmeldingService
import no.nav.syfo.isdialogmelding.client.IsDialogmeldingClient
import no.nav.syfo.istilgangskontroll.IsTilgangskontrollService
import no.nav.syfo.istilgangskontroll.client.IIsTilgangskontrollClient
import no.nav.syfo.oppfolgingsplan.api.v1.registerApiV1
import no.nav.syfo.oppfolgingsplan.dto.OppfolgingsplanResponse
import no.nav.syfo.oppfolgingsplan.dto.SykmeldtOppfolgingsplanOverviewResponse
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.pdfgen.PdfGenService
import no.nav.syfo.pdl.PdlService
import no.nav.syfo.persistOppfolgingsplan
import no.nav.syfo.persistOppfolgingsplanUtkast
import no.nav.syfo.plugins.installContentNegotiation
import no.nav.syfo.plugins.installStatusPages
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.texas.client.TexasIntrospectionResponse
import no.nav.syfo.varsel.EsyfovarselProducer
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

class OppfolgingsplanApiV1Test :
    DescribeSpec({

        val texasClientMock = mockk<TexasHttpClient>()
        val dineSykmeldteHttpClientMock = mockk<DineSykmeldteHttpClient>()
        val valkeyCacheMock = mockk<ValkeyCache>(relaxUnitFun = true)
        val esyfovarselProducerMock = mockk<EsyfovarselProducer>()
        val testDb = TestDB.database
        val narmestelederId = UUID.randomUUID().toString()
        val pdfGenService = mockk<PdfGenService>()
        val isDialogmeldingClientMock = mockk<IsDialogmeldingClient>()
        val dokarkivServiceMock = mockk<DokarkivService>()
        val isTilgangskontrollClientMock = mockk<IIsTilgangskontrollClient>()
        val isTilgangskontrollServiceMock = IsTilgangskontrollService(isTilgangskontrollClientMock)
        val pdlServiceMock = mockk<PdlService>(relaxed = true)
        val environment: Environment = LocalEnvironment()
        val aaregServiceMock = mockk<AaregService>(relaxed = true)
        val foresporselServiceMock = mockk<ForesporselService>(relaxed = true)

        beforeTest {
            clearAllMocks()
            TestDB.clearAllData()
            every { valkeyCacheMock.getSykmeldt(any(), any()) } returns null
            coEvery { foresporselServiceMock.getSykmeldteArbeidsforhold(any(), any(), any()) } returns emptyList()
        }

        fun withTestApplication(
            fn: suspend ApplicationTestBuilder.() -> Unit,
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
                    installStatusPages()
                    routing {
                        registerApiV1(
                            DineSykmeldteService(dineSykmeldteHttpClientMock, valkeyCacheMock),
                            texasClientMock,
                            oppfolgingsplanService = OppfolgingsplanService(
                                database = testDb,
                                esyfovarselProducer = esyfovarselProducerMock,
                                pdlService = pdlServiceMock,
                                aaregService = aaregServiceMock,
                            ),
                            pdfGenService = pdfGenService,
                            isDialogmeldingService = IsDialogmeldingService(isDialogmeldingClientMock),
                            dokarkivService = dokarkivServiceMock,
                            isTilgangskontrollService = isTilgangskontrollServiceMock,
                            foresporselService = foresporselServiceMock,
                            environment = environment,
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
                it("GET /oppfolgingsplaner/oversikt should respond with Forbidden when client is not allowed") {
                    withTestApplication {
                        // Arrange
                        val sykmeldtFnr = "12345678901"
                        // Mock with WRONG client (syfomodiaperson trying to access sykmeldt route)
                        texasClientMock.defaultMocks(
                            pid = sykmeldtFnr,
                            clientId = environment.syfomodiapersonClientId,
                        )

                        // Act
                        val response = client.get {
                            url("/api/v1/sykmeldt/oppfolgingsplaner/oversikt")
                            bearerAuth("Bearer token")
                        }

                        // Assert
                        response.status shouldBe HttpStatusCode.Forbidden
                    }
                }
                it("GET /oppfolgingsplaner/oversikt should respond with OK when texas client gives active response") {
                    withTestApplication {
                        // Arrange
                        val sykmeldtFnr = "12345678901"
                        texasClientMock.defaultMocks(
                            pid = sykmeldtFnr,
                            clientId = environment.syfoOppfolgingsplanFrontendClientId,
                        )

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
                        texasClientMock.defaultMocks(acr = "Level3", clientId = environment.syfoOppfolgingsplanFrontendClientId)

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
                        } returns TexasIntrospectionResponse(
                            active = false,
                            sub = "user",
                            clientId = environment.syfoOppfolgingsplanFrontendClientId,
                        )

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
                        texasClientMock.defaultMocks(
                            pid = sykmeldtFnr,
                            clientId = environment.syfoOppfolgingsplanFrontendClientId,
                        )

                        val firstPlanUUID = testDb.persistOppfolgingsplan(
                            defaultPersistedOppfolgingsplan()
                                .copy(
                                    narmesteLederId = narmestelederId,
                                    evalueringsdato = LocalDate.now().minus(45, ChronoUnit.DAYS),
                                ),
                        )
                        val latestPlanUUID = testDb.persistOppfolgingsplan(
                            defaultPersistedOppfolgingsplan()
                                .copy(narmesteLederId = narmestelederId),
                        )
                        testDb.persistOppfolgingsplanUtkast(
                            defaultPersistedOppfolgingsplanUtkast()
                                .copy(
                                    narmesteLederId = narmestelederId,
                                    narmesteLederFnr = narmestelederFnr,
                                    sykmeldtFnr = sykmeldtFnr,
                                ),
                        )

                        // Act
                        val response = client.get {
                            url("/api/v1/sykmeldt/oppfolgingsplaner/oversikt")
                            bearerAuth("Bearer token")
                        }

                        // Assert
                        response.status shouldBe HttpStatusCode.OK
                        val overview = response.body<SykmeldtOppfolgingsplanOverviewResponse>()
                        overview.aktiveOppfolgingsplaner.firstOrNull()?.id shouldBe latestPlanUUID
                        overview.aktiveOppfolgingsplaner.first().organization.orgNumber shouldBe defaultPersistedOppfolgingsplan().organisasjonsnummer
                        overview.tidligerePlaner.size shouldBe 1
                        overview.tidligerePlaner.first().id shouldBe firstPlanUUID
                        overview.tidligerePlaner.first().organization.orgNumber shouldBe defaultPersistedOppfolgingsplan().organisasjonsnummer
                        overview.sykmeldteArbeidsforhold shouldBe emptyList()
                    }
                }

                it("GET /oppfolgingsplaner/oversikt should include sykmeldte arbeidsforhold and strip bearer prefix") {
                    val sykmeldtFnr = "12345678901"
                    val arbeidsforhold = listOf(
                        SykmeldtArbeidsforhold(
                            organisasjonsnummer = "111111111",
                            organisasjonsnavn = "Plan AS",
                            narmesteLederNavn = "Plan Leder",
                            foresporselStatus = ForesporselStatus.CAN_REQUEST,
                            foresporselTidspunkt = null,
                        ),
                    )
                    withTestApplication {
                        texasClientMock.defaultMocks(
                            pid = sykmeldtFnr,
                            clientId = environment.syfoOppfolgingsplanFrontendClientId,
                        )
                        coEvery {
                            foresporselServiceMock.getSykmeldteArbeidsforhold(sykmeldtFnr, "user-token", any())
                        } returns arbeidsforhold

                        val response = client.get {
                            url("/api/v1/sykmeldt/oppfolgingsplaner/oversikt")
                            header(HttpHeaders.Authorization, "Bearer user-token")
                        }

                        response.status shouldBe HttpStatusCode.OK
                        val overview = response.body<SykmeldtOppfolgingsplanOverviewResponse>()
                        overview.sykmeldteArbeidsforhold.shouldContainExactly(arbeidsforhold)
                    }
                }
            }
            describe("Be om plan") {
                it("POST /oppfolgingsplaner/be-om-plan should respond with Created and call service") {
                    val sykmeldtFnr = "12345678901"
                    withTestApplication {
                        texasClientMock.defaultMocks(
                            pid = sykmeldtFnr,
                            clientId = environment.syfoOppfolgingsplanFrontendClientId,
                        )
                        coEvery {
                            foresporselServiceMock.beOmPlan(sykmeldtFnr, "111111111", "user-token")
                        } returns Unit

                        val response = client.post("/api/v1/sykmeldt/oppfolgingsplaner/be-om-plan") {
                            header(HttpHeaders.Authorization, "Bearer user-token")
                            contentType(ContentType.Application.Json)
                            setBody("""{"organisasjonsnummer":"111111111"}""")
                        }

                        response.status shouldBe HttpStatusCode.Created
                        coVerify {
                            foresporselServiceMock.beOmPlan(sykmeldtFnr, "111111111", "user-token")
                        }
                    }
                }

                it("POST /oppfolgingsplaner/be-om-plan should respond with NotFound when no nearest leader exists") {
                    val sykmeldtFnr = "12345678901"
                    withTestApplication {
                        texasClientMock.defaultMocks(
                            pid = sykmeldtFnr,
                            clientId = environment.syfoOppfolgingsplanFrontendClientId,
                        )
                        coEvery {
                            foresporselServiceMock.beOmPlan(sykmeldtFnr, "111111111", "user-token")
                        } throws ApiErrorException.NotFound("Fant ingen aktiv nærmeste leder-relasjon for virksomheten")

                        val response = client.post("/api/v1/sykmeldt/oppfolgingsplaner/be-om-plan") {
                            header(HttpHeaders.Authorization, "Bearer user-token")
                            contentType(ContentType.Application.Json)
                            setBody("""{"organisasjonsnummer":"111111111"}""")
                        }

                        response.status shouldBe HttpStatusCode.NotFound
                    }
                }

                it("POST /oppfolgingsplaner/be-om-plan should respond with Conflict when request was sent recently") {
                    val sykmeldtFnr = "12345678901"
                    withTestApplication {
                        texasClientMock.defaultMocks(
                            pid = sykmeldtFnr,
                            clientId = environment.syfoOppfolgingsplanFrontendClientId,
                        )
                        coEvery {
                            foresporselServiceMock.beOmPlan(sykmeldtFnr, "111111111", "user-token")
                        } throws ApiErrorException.Conflict("Forespørsel om oppfølgingsplan er allerede sendt nylig")

                        val response = client.post("/api/v1/sykmeldt/oppfolgingsplaner/be-om-plan") {
                            header(HttpHeaders.Authorization, "Bearer user-token")
                            contentType(ContentType.Application.Json)
                            setBody("""{"organisasjonsnummer":"111111111"}""")
                        }

                        response.status shouldBe HttpStatusCode.Conflict
                    }
                }

                it("POST /oppfolgingsplaner/be-om-plan should respond with BadRequest for invalid organisasjonsnummer") {
                    val sykmeldtFnr = "12345678901"
                    withTestApplication {
                        texasClientMock.defaultMocks(
                            pid = sykmeldtFnr,
                            clientId = environment.syfoOppfolgingsplanFrontendClientId,
                        )

                        val response = client.post("/api/v1/sykmeldt/oppfolgingsplaner/be-om-plan") {
                            header(HttpHeaders.Authorization, "Bearer user-token")
                            contentType(ContentType.Application.Json)
                            setBody("""{"organisasjonsnummer":"123"}""")
                        }

                        response.status shouldBe HttpStatusCode.BadRequest
                    }
                }
            }
            it("GET /oppfolgingsplaner/{uuid} should respond with NotFound if oppfolgingsplan does not exist") {
                withTestApplication {
                    // Arrange
                    val sykmeldtFnr = "12345678901"
                    texasClientMock.defaultMocks(pid = sykmeldtFnr, clientId = environment.syfoOppfolgingsplanFrontendClientId)

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
                        texasClientMock.defaultMocks(sykmeldtFnr, clientId = environment.syfoOppfolgingsplanFrontendClientId)

                        val existingUUID = testDb.persistOppfolgingsplan(
                            defaultPersistedOppfolgingsplan()
                                .copy(narmesteLederId = narmestelederId),
                        )

                        // Act
                        val response = client.get {
                            url("/api/v1/sykmeldt/oppfolgingsplaner/$existingUUID")
                            bearerAuth("Bearer token")
                        }
                        // Assert
                        response.status shouldBe HttpStatusCode.OK
                        response.body<OppfolgingsplanResponse>()
                    }
                }

                it("GET /oppfolgingsplaner/{uuid} should respond with Not found when found and plan does not belong to logged in user") {
                    withTestApplication {
                        // Arrange
                        val sykmeldtFnr = "12345678901"

                        texasClientMock.defaultMocks(sykmeldtFnr, clientId = environment.syfoOppfolgingsplanFrontendClientId)

                        val existingUUID = testDb.persistOppfolgingsplan(
                            defaultPersistedOppfolgingsplan()
                                .copy(narmesteLederId = narmestelederId, sykmeldtFnr = "12345678902"),
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
                        val sykmeldtFnr = "12345678901"
                        texasClientMock.defaultMocks(
                            pid = sykmeldtFnr,
                            clientId = environment.syfoOppfolgingsplanFrontendClientId,
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

                        texasClientMock.defaultMocks(sykmeldtFnr, clientId = environment.syfoOppfolgingsplanFrontendClientId)

                        coEvery { pdfGenService.generatePdf(any()) } returns generatedPdfStandin

                        val existingUUID = testDb.persistOppfolgingsplan(
                            defaultPersistedOppfolgingsplan()
                                .copy(narmesteLederId = narmestelederId),
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

                        texasClientMock.defaultMocks(sykmeldtFnr, clientId = environment.syfoOppfolgingsplanFrontendClientId)

                        coEvery { pdfGenService.generatePdf(any()) } throws RuntimeException("Forced")

                        val existingUUID = testDb.persistOppfolgingsplan(
                            defaultPersistedOppfolgingsplan()
                                .copy(narmesteLederId = narmestelederId),
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
