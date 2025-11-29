package no.nav.syfo.oppfolgingsplan.api.v1.veileder

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.DescribeSpec
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
import io.ktor.http.HttpStatusCode
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
import no.nav.syfo.application.Environment
import no.nav.syfo.application.LocalEnvironment
import no.nav.syfo.application.valkey.ValkeyCache
import no.nav.syfo.defaultMocks
import no.nav.syfo.defaultPersistedOppfolgingsplan
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.dinesykmeldte.client.DineSykmeldteHttpClient
import no.nav.syfo.dokarkiv.DokarkivService
import no.nav.syfo.isdialogmelding.IsDialogmeldingService
import no.nav.syfo.isdialogmelding.client.IsDialogmeldingClient
import no.nav.syfo.istilgangskontroll.IsTilgangskontrollService
import no.nav.syfo.istilgangskontroll.client.IIsTilgangskontrollClient
import no.nav.syfo.oppfolgingsplan.api.v1.registerApiV1
import no.nav.syfo.oppfolgingsplan.db.setDeltMedVeilederTidspunkt
import no.nav.syfo.oppfolgingsplan.db.updateSkalDelesMedVeileder
import no.nav.syfo.oppfolgingsplan.domain.Fodselsnummer
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.pdfgen.PdfGenService
import no.nav.syfo.persistOppfolgingsplan
import no.nav.syfo.plugins.installContentNegotiation
import no.nav.syfo.plugins.installStatusPages
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.varsel.EsyfovarselProducer
import java.time.Instant
import java.util.*

class VeilederOppfolgingsplanApiV1Test : DescribeSpec({
    val texasClientMock = mockk<TexasHttpClient>()
    val dineSykmeldteHttpClientMock = mockk<DineSykmeldteHttpClient>()
    val valkeyCacheMock = mockk<ValkeyCache>(relaxUnitFun = true)
    val esyfovarselProducerMock = mockk<EsyfovarselProducer>()
    val testDb = TestDB.database
    val sykmeldtFnr = "12345678910"
    val narmestelederId = UUID.randomUUID().toString()
    val pdfGenService = mockk<PdfGenService>()
    val isDialogmeldingClientMock = mockk<IsDialogmeldingClient>()
    val dokarkivServiceMock = mockk<DokarkivService>()
    val isTilgangskontrollClientMock = mockk<IIsTilgangskontrollClient>()
    val isTilgangskontrollServiceMock = IsTilgangskontrollService(isTilgangskontrollClientMock)
    val environment: Environment = LocalEnvironment()
    val syfomodiapersonClientId = environment.syfomodiapersonClientId

    beforeTest {
        clearAllMocks()
        TestDB.clearAllData()
        every { valkeyCacheMock.getSykmeldt(any(), any()) } returns null
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
                installStatusPages()
                routing {
                    registerApiV1(
                        DineSykmeldteService(dineSykmeldteHttpClientMock, valkeyCacheMock),
                        texasClientMock,
                        oppfolgingsplanService = OppfolgingsplanService(
                            database = testDb,
                            esyfovarselProducer = esyfovarselProducerMock,
                            pdlService = mockk(relaxed = true),
                        ),
                        pdfGenService = pdfGenService,
                        isDialogmeldingService = IsDialogmeldingService(isDialogmeldingClientMock),
                        dokarkivService = dokarkivServiceMock,
                        isTilgangskontrollService = isTilgangskontrollServiceMock,
                        environment = environment,
                    )
                }
            }
            fn(this)
        }
    }

    describe("Veileder Oppfolgingsplan API") {
        describe("List") {
            it("POST /veileder/oppfolgingsplaner should respond with Unauthorized when no authentication is provided") {
                withTestApplication {
                    // Arrange
                    // Act
                    val response = client.post("/api/v1/veileder/oppfolgingsplaner/query")

                    // Assert
                    response.status shouldBe HttpStatusCode.Unauthorized
                }
            }

            it("POST /veileder/oppfolgingsplaner should respond with Unauthorized if token is lacking NAVident") {
                withTestApplication {
                    // Arrange
                    texasClientMock.defaultMocks(pid = "some-veileder-token")
                    coEvery { isTilgangskontrollClientMock.harTilgangTilSykmeldt(any(), any()) } returns true

                    // Act
                    val response = client.post {
                        url("/api/v1/veileder/oppfolgingsplaner/query")
                        bearerAuth(token = "Bearer token")
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.Unauthorized
                    coVerify(exactly = 0) {
                        isTilgangskontrollClientMock.harTilgangTilSykmeldt(
                            sykmeldtFnr = any(), token = any()
                        )
                    }
                }
            }

            it("POST /veileder/oppfolgingsplaner should respond with Forbidden when client is not allowed") {
                withTestApplication {
                    // Arrange
                    // Mock with WRONG client (frontend trying to access veileder route)
                    texasClientMock.defaultMocks(
                        pid = "some-token",
                        navident = "some-navident",
                        azp = environment.syfoOppfolgingsplanFrontendClientId
                    )
                    coEvery { isTilgangskontrollClientMock.harTilgangTilSykmeldt(any(), any()) } returns true

                    // Act
                    val response = client.post {
                        url("/api/v1/veileder/oppfolgingsplaner/query")
                        bearerAuth(token = "Bearer token")
                        contentType(ContentType.Application.Json)
                        setBody(OppfolgingsplanerReadRequest(sykmeldtFnr))
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.Forbidden
                }
            }

            it("POST /veileder/oppfolgingsplaner should respond with Bad Request if sykmeldt fnr is not provided in body") {
                withTestApplication {
                    // Arrange
                    texasClientMock.defaultMocks(
                        pid = "some-veileder-token",
                        navident = "some-navident",
                        azp = syfomodiapersonClientId
                    )
                    coEvery { isTilgangskontrollClientMock.harTilgangTilSykmeldt(any(), any()) } returns true

                    // Act
                    val response = client.post {
                        url("/api/v1/veileder/oppfolgingsplaner/query")
                        bearerAuth(token = "Bearer token")
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.BadRequest
                    coVerify(exactly = 0) {
                        isTilgangskontrollClientMock.harTilgangTilSykmeldt(
                            sykmeldtFnr = any(), token = any()
                        )
                    }
                }
            }

            it("POST /veileder/oppfolgingsplaner should respond with Forbidden when Tilgangskontroll rejects access to sykemeldt") {
                withTestApplication {
                    // Arrange
                    texasClientMock.defaultMocks(
                        pid = "some-veileder-token",
                        navident = "some-navident",
                        azp = syfomodiapersonClientId
                    )
                    coEvery { isTilgangskontrollClientMock.harTilgangTilSykmeldt(any(), any()) } returns false

                    // Act
                    val response = client.post {
                        url("/api/v1/veileder/oppfolgingsplaner/query")
                        bearerAuth(token = "Bearer token")
                        contentType(ContentType.Application.Json)
                        setBody(OppfolgingsplanerReadRequest(sykmeldtFnr))
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.Forbidden
                    coVerify(exactly = 1) {
                        isTilgangskontrollClientMock.harTilgangTilSykmeldt(
                            sykmeldtFnr = eq(Fodselsnummer(sykmeldtFnr)), token = any()
                        )
                    }
                }
            }

            it("POST /veileder/oppfolgingsplaner should respond with OK when correct authentication is provided") {
                withTestApplication {
                    // Arrange
                    texasClientMock.defaultMocks(
                        pid = "some-veileder-token",
                        navident = "some-navident",
                        azp = syfomodiapersonClientId
                    )
                    coEvery { isTilgangskontrollClientMock.harTilgangTilSykmeldt(any(), any()) } returns true

                    val firstPlanUUID = testDb.persistOppfolgingsplan(
                        defaultPersistedOppfolgingsplan().copy(
                            narmesteLederId = narmestelederId,
                            sykmeldtFnr = sykmeldtFnr,
                        )
                    )
                    testDb.updateSkalDelesMedVeileder(firstPlanUUID, true)
                    testDb.setDeltMedVeilederTidspunkt(firstPlanUUID, Instant.now())
                    testDb.persistOppfolgingsplan(
                        defaultPersistedOppfolgingsplan().copy(
                            narmesteLederId = narmestelederId,
                            sykmeldtFnr = sykmeldtFnr,
                        )
                    )

                    // Act
                    val response = client.post {
                        url("/api/v1/veileder/oppfolgingsplaner/query")
                        bearerAuth(token = "Bearer token")
                        header(NAV_PERSONIDENT_HEADER, sykmeldtFnr)
                        contentType(ContentType.Application.Json)
                        setBody(OppfolgingsplanerReadRequest(sykmeldtFnr))
                    }

                    // Assert
                    response.status shouldBe HttpStatusCode.OK
                    val responseBody = response.body<List<OppfolgingsplanVeileder>>()
                    responseBody.size shouldBe 1
                    coVerify(exactly = 1) {
                        isTilgangskontrollClientMock.harTilgangTilSykmeldt(
                            sykmeldtFnr = eq(Fodselsnummer(sykmeldtFnr)), token = any()
                        )
                    }
                    val responseList = response.body<List<OppfolgingsplanVeileder>>()
                    responseList.size shouldBe 1
                    responseList.first().uuid shouldBe firstPlanUUID
                }
            }

        }
    }

    describe("PDF for uuid") {
        it("GET /veileder/oppfolgingsplaner/<uuid> should respond with Not Found when there is no oppfolgingsplan delt with nav for the uuid") {
            withTestApplication {
                // Arrange
                val pdfContent = "ThisIsPdfContent"
                texasClientMock.defaultMocks(
                    pid = "some-veileder-token",
                    navident = "some-navident",
                    azp = syfomodiapersonClientId
                )
                coEvery { isTilgangskontrollClientMock.harTilgangTilSykmeldt(any(), any()) } returns true
                coEvery { pdfGenService.generatePdf(any()) } returns pdfContent.toByteArray(Charsets.UTF_8)
                val firstPlanUUID = testDb.persistOppfolgingsplan(
                    defaultPersistedOppfolgingsplan().copy(
                        narmesteLederId = narmestelederId,
                        sykmeldtFnr = sykmeldtFnr,
                    )
                )

                // Act
                val response = client.get {
                    url("/api/v1/veileder/oppfolgingsplaner/${firstPlanUUID}")
                    bearerAuth(token = "Bearer token")
                    header(NAV_PERSONIDENT_HEADER, sykmeldtFnr)
                }

                // Assert
                response.status shouldBe HttpStatusCode.NotFound
                coVerify(exactly = 0) {
                    isTilgangskontrollClientMock.harTilgangTilSykmeldt(
                        sykmeldtFnr = eq(Fodselsnummer(sykmeldtFnr)), token = any()
                    )
                }
            }
        }
        it("GET /veileder/oppfolgingsplaner/<uuid> should respond with forbidden when Tilgangskontroll rejects access to sykemeldt") {
            withTestApplication {
                // Arrange
                texasClientMock.defaultMocks(
                    pid = "some-veileder-token",
                    navident = "some-navident",
                    azp = syfomodiapersonClientId
                )
                coEvery { isTilgangskontrollClientMock.harTilgangTilSykmeldt(any(), any()) } returns false
                val firstPlanUUID = testDb.persistOppfolgingsplan(
                    defaultPersistedOppfolgingsplan().copy(
                        narmesteLederId = narmestelederId,
                        sykmeldtFnr = sykmeldtFnr,
                    )
                )
                testDb.updateSkalDelesMedVeileder(firstPlanUUID, true)
                testDb.setDeltMedVeilederTidspunkt(firstPlanUUID, Instant.now())
                testDb.persistOppfolgingsplan(
                    defaultPersistedOppfolgingsplan().copy(
                        narmesteLederId = narmestelederId,
                        sykmeldtFnr = sykmeldtFnr,
                    )
                )

                // Act
                val response = client.get {
                    url("/api/v1/veileder/oppfolgingsplaner/${firstPlanUUID}")
                    bearerAuth(token = "Bearer token")
                    header(NAV_PERSONIDENT_HEADER, sykmeldtFnr)
                }

                // Assert
                response.status shouldBe HttpStatusCode.Forbidden
                coVerify(exactly = 1) {
                    isTilgangskontrollClientMock.harTilgangTilSykmeldt(
                        sykmeldtFnr = eq(Fodselsnummer(sykmeldtFnr)), token = any()
                    )
                }
            }
        }

        it("GET /veileder/oppfolgingsplaner/<uuid> should respond with OK and pdf as ByteArray") {
            withTestApplication {
                // Arrange
                val pdfContent = "ThisIsPdfContent"
                texasClientMock.defaultMocks(
                    pid = "some-veileder-token",
                    navident = "some-navident",
                    azp = syfomodiapersonClientId
                )
                coEvery { isTilgangskontrollClientMock.harTilgangTilSykmeldt(any(), any()) } returns true
                coEvery { pdfGenService.generatePdf(any()) } returns pdfContent.toByteArray(Charsets.UTF_8)
                val firstPlanUUID = testDb.persistOppfolgingsplan(
                    defaultPersistedOppfolgingsplan().copy(
                        narmesteLederId = narmestelederId,
                        sykmeldtFnr = sykmeldtFnr,
                    )
                )
                testDb.updateSkalDelesMedVeileder(firstPlanUUID, true)
                testDb.setDeltMedVeilederTidspunkt(firstPlanUUID, Instant.now())
                testDb.persistOppfolgingsplan(
                    defaultPersistedOppfolgingsplan().copy(
                        narmesteLederId = narmestelederId,
                        sykmeldtFnr = sykmeldtFnr,
                    )
                )

                // Act
                val response = client.get {
                    url("/api/v1/veileder/oppfolgingsplaner/${firstPlanUUID}")
                    bearerAuth(token = "Bearer token")
                    header(NAV_PERSONIDENT_HEADER, sykmeldtFnr)
                }

                // Assert
                response.status shouldBe HttpStatusCode.OK
                response.contentType() shouldBe ContentType.Application.Pdf
                response.body<ByteArray>() shouldBe pdfContent.toByteArray(Charsets.UTF_8)
                coVerify(exactly = 1) {
                    isTilgangskontrollClientMock.harTilgangTilSykmeldt(
                        sykmeldtFnr = eq(Fodselsnummer(sykmeldtFnr)), token = any()
                    )
                }
            }
        }
    }
})
