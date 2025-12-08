package no.nav.syfo.oppfolgingsplan.api.v1.arbeidsgiver

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.call.body
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
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.TestDB
import no.nav.syfo.application.Environment
import no.nav.syfo.application.LocalEnvironment
import no.nav.syfo.application.exception.ApiError
import no.nav.syfo.application.exception.ErrorType
import no.nav.syfo.application.exception.LegeNotFoundException
import no.nav.syfo.application.valkey.ValkeyCache
import no.nav.syfo.defaultMocks
import no.nav.syfo.defaultOppfolgingsplan
import no.nav.syfo.defaultPersistedOppfolgingsplan
import no.nav.syfo.defaultSykmeldt
import no.nav.syfo.defaultUtkastRequest
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.dinesykmeldte.client.DineSykmeldteHttpClient
import no.nav.syfo.dokarkiv.DokarkivService
import no.nav.syfo.generatedPdfStandin
import no.nav.syfo.isdialogmelding.IsDialogmeldingService
import no.nav.syfo.isdialogmelding.client.IsDialogmeldingClient
import no.nav.syfo.istilgangskontroll.IsTilgangskontrollService
import no.nav.syfo.istilgangskontroll.client.IIsTilgangskontrollClient
import no.nav.syfo.oppfolgingsplan.api.v1.registerApiV1
import no.nav.syfo.oppfolgingsplan.db.findAllOppfolgingsplanerBy
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanUtkastBy
import no.nav.syfo.oppfolgingsplan.db.upsertOppfolgingsplanUtkast
import no.nav.syfo.oppfolgingsplan.dto.ArbeidsgiverFerdigstiltOppfolgingsplanResponse
import no.nav.syfo.oppfolgingsplan.dto.ArbeidsgiverOppfolgingsplanOverviewResponse
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.pdfgen.PdfGenService
import no.nav.syfo.pdl.PdlService
import no.nav.syfo.persistOppfolgingsplan
import no.nav.syfo.plugins.installContentNegotiation
import no.nav.syfo.plugins.installStatusPages
import no.nav.syfo.returnsNotFound
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.texas.client.TexasIntrospectionResponse
import no.nav.syfo.varsel.EsyfovarselProducer
import no.nav.syfo.varsel.domain.ArbeidstakerHendelse
import java.time.Instant
import java.util.*

class OppfolgingsplanApiV1Test : DescribeSpec({

    val texasClientMock = mockk<TexasHttpClient>()
    val dineSykmeldteHttpClientMock = mockk<DineSykmeldteHttpClient>()
    val valkeyCacheMock = mockk<ValkeyCache>(relaxUnitFun = true)
    val esyfovarselProducerMock = mockk<EsyfovarselProducer>()
    val testDb = TestDB.database
    val isDialogmeldingClientMock = mockk<IsDialogmeldingClient>()
    val isTilgangskontrollClientMock = mockk<IIsTilgangskontrollClient>()
    val pdfGenServiceMock = mockk<PdfGenService>()
    val pdlServiceMock = mockk<PdlService>(relaxed = true)

    val narmestelederId = UUID.randomUUID().toString()
    val pidInnlogetBruker = "10987654321"
    val sykmeldt = defaultSykmeldt().copy(narmestelederId = narmestelederId)

    val dokarkivServiceMock = mockk<DokarkivService>()
    val isTilgangskontrollServiceMock = IsTilgangskontrollService(isTilgangskontrollClientMock)

    beforeTest {
        clearAllMocks()
        TestDB.clearAllData()
        every { valkeyCacheMock.getSykmeldt(any(), any()) } returns null
    }
    val oppfolgingsplanService = OppfolgingsplanService(
        database = testDb,
        esyfovarselProducer = esyfovarselProducerMock,
        pdlService = pdlServiceMock,
    )
    val environment: Environment = LocalEnvironment()

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
                        oppfolgingsplanService = oppfolgingsplanService,
                        pdfGenService = pdfGenServiceMock,
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

    describe("Oppfolgingsplan API") {
        it("GET /oppfolgingsplaner/oversikt should respond with Unauthorized when no authentication is provided") {
            withTestApplication {
                // Act
                val response = client.get("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/oversikt")

                // Assert
                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }
        it("GET /oppfolgingsplaner/oversikt should respond with Unauthorized when no bearer token is provided") {
            withTestApplication {
                // Act
                val response = client.get {
                    url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/oversikt")
                    bearerAuth("")
                }

                // Assert
                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }
        it("GET /oppfolgingsplaner/oversikt should respond with Forbidden when client is not allowed") {
            withTestApplication {
                // Arrange
                texasClientMock.defaultMocks(azpName = environment.syfomodiapersonClientId) // Wrong client!
                dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

                // Act
                val response = client.get {
                    url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/oversikt")
                    bearerAuth("Bearer token")
                }

                // Assert
                response.status shouldBe HttpStatusCode.Forbidden
            }
        }
        it("GET /oppfolgingsplaner/oversikt should respond with OK when texas client gives active response") {
            withTestApplication {
                // Arrange
                texasClientMock.defaultMocks(clientId = environment.syfoOppfolgingsplanFrontendClientId)
                coEvery {
                    dineSykmeldteHttpClientMock.getSykmeldtForNarmesteLederId(narmestelederId, "token")
                } returns defaultSykmeldt().copy(narmestelederId = narmestelederId)

                // Act
                val response = client.get {
                    url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/oversikt")
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
                    url("api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/oversikt")
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
                    clientId = environment.syfoOppfolgingsplanFrontendClientId
                )

                // Act
                val response = client.get {
                    url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/oversikt")
                    bearerAuth("Bearer token")
                }

                // Assert
                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }
        it("GET /oppfolgingsplaner/oversikt should respond with SYKMELDT_NOT_FOUND when dine sykmeldte returns not found") {
            withTestApplication {
                // Arrange
                texasClientMock.defaultMocks(clientId = environment.syfoOppfolgingsplanFrontendClientId)

                dineSykmeldteHttpClientMock.returnsNotFound(narmestelederId = narmestelederId)

                // Act
                val response = client.get {
                    url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/oversikt")
                    bearerAuth("Bearer token")
                }

                // Assert
                response.status shouldBe HttpStatusCode.NotFound
                val apiError = response.body<ApiError>()
                apiError.type shouldBe ErrorType.SYKMELDT_NOT_FOUND
            }
        }
        it("GET /oppfolgingsplaner/{uuid} should respond with NotFound if oppfolgingsplan does not exist") {
            withTestApplication {
                // Arrange
                texasClientMock.defaultMocks(clientId = environment.syfoOppfolgingsplanFrontendClientId)

                dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

                // Act
                val response = client.get {
                    url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/00000000-0000-0000-0000-000000000000")
                    bearerAuth("Bearer token")
                }

                // Assert
                response.status shouldBe HttpStatusCode.NotFound
                val apiError = response.body<ApiError>()
                apiError.type shouldBe ErrorType.PLAN_NOT_FOUND
            }
        }

        it("GET /oppfolgingsplaner/{uuid} should respond with OK and return oppfolgingsplan when found and authorized") {
            withTestApplication {
                // Arrange
                texasClientMock.defaultMocks(clientId = environment.syfoOppfolgingsplanFrontendClientId)

                dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

                val existingUUID = testDb.persistOppfolgingsplan(
                    defaultPersistedOppfolgingsplan()
                        .copy(
                            narmesteLederId = narmestelederId,
                        )
                )

                // Act
                val response = client.get {
                    url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/$existingUUID")
                    bearerAuth("Bearer token")
                }

                // Assert
                response.status shouldBe HttpStatusCode.OK
                val body = response.body<ArbeidsgiverFerdigstiltOppfolgingsplanResponse>()
                body.hasUtkast shouldBe false
            }
        }

        it("GET /oppfolgingsplaner/{uuid} should return hasUtkast=true when utkast exists") {
            withTestApplication {
                // Arrange
                texasClientMock.defaultMocks(clientId = environment.syfoOppfolgingsplanFrontendClientId)

                dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

                val existingUUID = testDb.persistOppfolgingsplan(
                    defaultPersistedOppfolgingsplan()
                        .copy(
                            narmesteLederId = narmestelederId,
                        )
                )

                testDb.upsertOppfolgingsplanUtkast(
                    narmesteLederFnr = pidInnlogetBruker,
                    sykmeldt = sykmeldt,
                    lagreUtkastRequest = defaultUtkastRequest()
                )

                // Act
                val response = client.get {
                    url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/$existingUUID")
                    bearerAuth("Bearer token")
                }

                // Assert
                response.status shouldBe HttpStatusCode.OK
                val body = response.body<ArbeidsgiverFerdigstiltOppfolgingsplanResponse>()
                body.hasUtkast shouldBe true
            }
        }

        it("GET /oppfolgingsplaner/aktiv-plan should respond with PLAN_NOT_FOUND if aktiv plan does not exist") {
            withTestApplication {
                // Arrange
                texasClientMock.defaultMocks(clientId = environment.syfoOppfolgingsplanFrontendClientId)

                dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

                // Act
                val response = client.get {
                    url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/aktiv-plan")
                    bearerAuth("Bearer token")
                }

                // Assert
                response.status shouldBe HttpStatusCode.NotFound
                val apiError = response.body<ApiError>()
                apiError.type shouldBe ErrorType.PLAN_NOT_FOUND
            }
        }

        it("GET /oppfolgingsplaner/aktiv-plan should respond with OK and return oppfolgingsplan when found and authorized") {
            withTestApplication {
                // Arrange
                texasClientMock.defaultMocks(clientId = environment.syfoOppfolgingsplanFrontendClientId)

                dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

                testDb.persistOppfolgingsplan(
                    defaultPersistedOppfolgingsplan()
                        .copy(
                            narmesteLederId = narmestelederId,
                        )
                )

                // Act
                val response = client.get {
                    url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/aktiv-plan")
                    bearerAuth("Bearer token")
                }

                // Assert
                response.status shouldBe HttpStatusCode.OK
                val body = response.body<ArbeidsgiverFerdigstiltOppfolgingsplanResponse>()
                body.hasUtkast shouldBe false
            }
        }

        it("GET /oppfolgingsplaner/aktiv-plan should return hasUtkast=true when utkast exists") {
            withTestApplication {
                // Arrange
                texasClientMock.defaultMocks(clientId = environment.syfoOppfolgingsplanFrontendClientId)

                dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

                testDb.persistOppfolgingsplan(
                    defaultPersistedOppfolgingsplan()
                        .copy(
                            narmesteLederId = narmestelederId,
                        )
                )

                testDb.upsertOppfolgingsplanUtkast(
                    narmesteLederFnr = pidInnlogetBruker,
                    sykmeldt = sykmeldt,
                    lagreUtkastRequest = defaultUtkastRequest()
                )

                // Act
                val response = client.get {
                    url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/aktiv-plan")
                    bearerAuth("Bearer token")
                }

                // Assert
                response.status shouldBe HttpStatusCode.OK
                val body = response.body<ArbeidsgiverFerdigstiltOppfolgingsplanResponse>()
                body.hasUtkast shouldBe true
            }
        }

        it("GET /oppfolgingsplaner/oversikt should respond with OK and return overview") {
            withTestApplication {
                // Arrange
                texasClientMock.defaultMocks(clientId = environment.syfoOppfolgingsplanFrontendClientId)

                dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

                val firstPlanUUID = testDb.persistOppfolgingsplan(
                    defaultPersistedOppfolgingsplan()
                        .copy(
                            narmesteLederId = narmestelederId,
                        )
                )
                val latestPlanUUID = testDb.persistOppfolgingsplan(
                    defaultPersistedOppfolgingsplan()
                        .copy(
                            narmesteLederId = narmestelederId,
                        )
                )
                testDb.upsertOppfolgingsplanUtkast(
                    narmesteLederFnr = pidInnlogetBruker,
                    sykmeldt = sykmeldt,
                    lagreUtkastRequest = defaultUtkastRequest()
                )

                // Act
                val response = client.get {
                    url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/oversikt")
                    bearerAuth("Bearer token")
                }

                // Assert
                response.status shouldBe HttpStatusCode.OK
                val overview = response.body<ArbeidsgiverOppfolgingsplanOverviewResponse>().oversikt
                overview.aktivPlan?.id shouldBe latestPlanUUID
                overview.tidligerePlaner.size shouldBe 1
                overview.tidligerePlaner.first().id shouldBe firstPlanUUID
            }
        }
        it("POST /oppfolgingsplaner should respond with 201 when oppfolgingsplan is created successfully") {
            withTestApplication {
                // Arrange
                texasClientMock.defaultMocks(
                    pidInnlogetBruker,
                    clientId = environment.syfoOppfolgingsplanFrontendClientId
                )

                dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

                coEvery {
                    esyfovarselProducerMock.sendVarselToEsyfovarsel(any())
                } returns Unit

                val oppfolgingsplan = defaultOppfolgingsplan()

                // Act
                val response = client.post {
                    url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner")
                    bearerAuth("Bearer token")
                    contentType(ContentType.Application.Json)
                    setBody(oppfolgingsplan)
                }
                // Assert
                response.status shouldBe HttpStatusCode.Created

                val persisted = testDb.findAllOppfolgingsplanerBy("12345678901", "orgnummer")
                persisted.size shouldBe 1
                persisted.first().sykmeldtFnr shouldBe sykmeldt.fnr
                persisted.first().narmesteLederFnr shouldBe pidInnlogetBruker
                persisted.first().narmesteLederId shouldBe narmestelederId
                persisted.first().organisasjonsnummer shouldBe sykmeldt.orgnummer
                persisted.first().content.toString() shouldBe oppfolgingsplan.content.toString()
                persisted.first().evalueringsdato.toString() shouldBe oppfolgingsplan.evalueringsdato.toString()
                persisted.first().sykmeldtFullName shouldBe "Navn Sykmeldt"
                persisted.first().organisasjonsnavn shouldBe "Test AS"
                verify(exactly = 1) {
                    esyfovarselProducerMock.sendVarselToEsyfovarsel(withArg {
                        val hendelse = it as ArbeidstakerHendelse
                        hendelse.arbeidstakerFnr shouldBe sykmeldt.fnr
                        hendelse.orgnummer shouldBe sykmeldt.orgnummer
                    })
                }
            }
        }
        it("POST /oppfolgingsplaner creates new oppfolgingsplan and deletes existing utkast for narmesteLederId") {
            withTestApplication {
                // Arrange
                texasClientMock.defaultMocks(
                    pidInnlogetBruker,
                    clientId = environment.syfoOppfolgingsplanFrontendClientId
                )

                dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

                coEvery {
                    esyfovarselProducerMock.sendVarselToEsyfovarsel(any())
                } returns Unit

                testDb.upsertOppfolgingsplanUtkast(
                    narmesteLederFnr = pidInnlogetBruker,
                    sykmeldt = sykmeldt,
                    lagreUtkastRequest = defaultUtkastRequest()
                )

                val existingUtkast = testDb.findOppfolgingsplanUtkastBy(sykmeldt.fnr, sykmeldt.orgnummer)

                val oppfolgingsplan = defaultOppfolgingsplan()

                // Act
                val response = client.post {
                    url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner")
                    bearerAuth("Bearer token")
                    contentType(ContentType.Application.Json)
                    setBody(oppfolgingsplan)
                }

                // Assert
                response.status shouldBe HttpStatusCode.Created
                val persistedOppfolgingsplaner = testDb.findAllOppfolgingsplanerBy("12345678901", "orgnummer")
                persistedOppfolgingsplaner.size shouldBe 1
                persistedOppfolgingsplaner.first().utkastCreatedAt shouldBe existingUtkast?.createdAt

                val persistedUtkast = testDb.findOppfolgingsplanUtkastBy(sykmeldt.fnr, sykmeldt.orgnummer)
                persistedUtkast shouldBe null
                verify(exactly = 1) {
                    esyfovarselProducerMock.sendVarselToEsyfovarsel(withArg {
                        val hendelse = it as ArbeidstakerHendelse
                        hendelse.arbeidstakerFnr shouldBe sykmeldt.fnr
                        hendelse.orgnummer shouldBe sykmeldt.orgnummer
                    })
                }
            }
        }
    }
    it("POST /oppfolgingsplaner still creates new oppfolgingsplan when kafka producer throws exception") {
        withTestApplication {
            // Arrange
            texasClientMock.defaultMocks(pidInnlogetBruker, clientId = environment.syfoOppfolgingsplanFrontendClientId)

            dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

            coEvery {
                esyfovarselProducerMock.sendVarselToEsyfovarsel(any())
            } throws Exception("exception")

            testDb.upsertOppfolgingsplanUtkast(
                narmesteLederFnr = pidInnlogetBruker,
                sykmeldt = sykmeldt,
                lagreUtkastRequest = defaultUtkastRequest()
            )

            val oppfolgingsplan = defaultOppfolgingsplan()

            // Act
            val response = client.post {
                url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner")
                bearerAuth("Bearer token")
                contentType(ContentType.Application.Json)
                setBody(oppfolgingsplan)
            }

            // Assert
            response.status shouldBe HttpStatusCode.Created
            val persistedOppfolgingsplaner = testDb.findAllOppfolgingsplanerBy("12345678901", "orgnummer")
            persistedOppfolgingsplaner.size shouldBe 1

            val persistedUtkast = testDb.findOppfolgingsplanUtkastBy("12345678901", "orgnummer")
            persistedUtkast shouldBe null
            verify(exactly = 1) {
                esyfovarselProducerMock.sendVarselToEsyfovarsel(withArg {
                    val hendelse = it as ArbeidstakerHendelse
                    hendelse.arbeidstakerFnr shouldBe sykmeldt.fnr
                    hendelse.orgnummer shouldBe sykmeldt.orgnummer
                })
            }
        }
    }
    it("POST /oppfolgingsplaner/{uuid}/del-med-lege should respond with NotFound if plan does not exist") {
        withTestApplication {
            // Arrange
            texasClientMock.defaultMocks(pidInnlogetBruker, clientId = environment.syfoOppfolgingsplanFrontendClientId)

            dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

            val uuid = UUID.randomUUID()
            // Act
            val response = client.post {
                url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/$uuid/del-med-lege")
                bearerAuth("Bearer token")
            }
            // Assert
            response.status shouldBe HttpStatusCode.NotFound
            val apiError = response.body<ApiError>()
            apiError.message shouldBe "Oppfolgingsplan not found for uuid: $uuid"
            apiError.type shouldBe ErrorType.PLAN_NOT_FOUND
        }
    }

    it("POST /oppfolgingsplaner/{uuid}/del-med-lege should respond with LEGE_NOT_FOUND if couldnt send to lege") {
        withTestApplication {
            // Arrange
            texasClientMock.defaultMocks(pidInnlogetBruker, clientId = environment.syfoOppfolgingsplanFrontendClientId)

            dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

            coEvery { pdfGenServiceMock.generatePdf(any()) } returns generatedPdfStandin

            coEvery {
                isDialogmeldingClientMock.sendOppfolgingsplanToGeneralPractitioner(any(), any(), any())
            } throws LegeNotFoundException("Lege not found for sykmeldt")

            val uuid = testDb.persistOppfolgingsplan(
                defaultPersistedOppfolgingsplan()
                    .copy(narmesteLederId = narmestelederId)
            )
            // Act
            val response = client.post {
                url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/$uuid/del-med-lege")
                bearerAuth("Bearer token")
            }
            // Assert
            val plan = testDb.findAllOppfolgingsplanerBy("12345678901", "orgnummer").first { it.uuid == uuid }
            plan.skalDelesMedLege shouldBe true
            plan.deltMedLegeTidspunkt shouldBe null

            response.status shouldBe HttpStatusCode.NotFound
            val apiError = response.body<ApiError>()
            apiError.type shouldBe ErrorType.LEGE_NOT_FOUND
        }
    }

    it("POST /oppfolgingsplaner/{uuid}/del-med-lege should respond with OK and update plan when authorized") {
        withTestApplication {
            // Arrange
            texasClientMock.defaultMocks(pidInnlogetBruker, clientId = environment.syfoOppfolgingsplanFrontendClientId)

            dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

            coEvery { pdfGenServiceMock.generatePdf(any()) } returns generatedPdfStandin

            coEvery {
                isDialogmeldingClientMock.sendOppfolgingsplanToGeneralPractitioner(
                    any(),
                    any(),
                    any()
                )
            } returns Unit
            val uuid = testDb.persistOppfolgingsplan(
                defaultPersistedOppfolgingsplan()
                    .copy(narmesteLederId = narmestelederId)
            )
            // Act
            val response = client.post {
                url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/$uuid/del-med-lege")
                bearerAuth("Bearer token")
            }
            // Assert
            response.status shouldBe HttpStatusCode.OK
            val plan = testDb.findAllOppfolgingsplanerBy("12345678901", "orgnummer").first { it.uuid == uuid }
            plan.skalDelesMedLege shouldBe true
            plan.deltMedLegeTidspunkt shouldNotBe null
        }
    }

    it("POST /oppfolgingsplaner/{uuid}/del-med-lege should respond with NOT FOUND when isDialogmeldingClient throws LegeNotFoundException") {
        withTestApplication {
            // Arrange
            texasClientMock.defaultMocks(pidInnlogetBruker, clientId = environment.syfoOppfolgingsplanFrontendClientId)

            dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

            coEvery { pdfGenServiceMock.generatePdf(any()) } returns generatedPdfStandin

            coEvery {
                isDialogmeldingClientMock.sendOppfolgingsplanToGeneralPractitioner(
                    any(),
                    any(),
                    any()
                )
            } throws LegeNotFoundException("Lege not found for sykmeldt")
            val uuid = testDb.persistOppfolgingsplan(
                defaultPersistedOppfolgingsplan()
                    .copy(narmesteLederId = narmestelederId)
            )
            // Act
            val response = client.post {
                url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/$uuid/del-med-lege")
                bearerAuth("Bearer token")
            } // Assert
            response.status shouldBe HttpStatusCode.NotFound
            val apiError = response.body<ApiError>()
            apiError.message shouldBe "Lege not found for sykmeldt"
            apiError.type shouldBe ErrorType.LEGE_NOT_FOUND
            val plan = testDb.findAllOppfolgingsplanerBy("12345678901", "orgnummer").first { it.uuid == uuid }
            plan.skalDelesMedLege shouldBe true
            plan.deltMedLegeTidspunkt shouldBe null
        }
    }

    it("POST /oppfolgingsplaner/{uuid}/del-med-veileder should respond with NotFound if plan does not exist") {
        withTestApplication {
            // Arrange
            texasClientMock.defaultMocks(pidInnlogetBruker, clientId = environment.syfoOppfolgingsplanFrontendClientId)

            dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

            val uuid = UUID.randomUUID()
            // Act
            val response = client.post {
                url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/$uuid/del-med-veileder")
                bearerAuth("Bearer token")
            }
            // Assert
            response.status shouldBe HttpStatusCode.NotFound
            val apiError = response.body<ApiError>()
            apiError.message shouldBe "Oppfolgingsplan not found for uuid: $uuid"
            coVerify(exactly = 0) {
                dokarkivServiceMock.arkiverOppfolgingsplan(any(), any())
            }
        }
    }

    it("POST /oppfolgingsplaner/{uuid}/del-med-veileder should respond with Conflict if plan is already shared with Nav") {
        withTestApplication {
            // Arrange
            texasClientMock.defaultMocks(pidInnlogetBruker, clientId = environment.syfoOppfolgingsplanFrontendClientId)

            dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

            val uuid = testDb.persistOppfolgingsplan(
                defaultPersistedOppfolgingsplan()
                    .copy(
                        narmesteLederId = narmestelederId,
                        skalDelesMedVeileder = true,
                    )
            )
            oppfolgingsplanService.setDeltMedVeilederTidspunkt(uuid, Instant.now())
            // Act
            val response = client.post {
                url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/$uuid/del-med-veileder")
                bearerAuth("Bearer token")
            }
            // Assert
            response.status shouldBe HttpStatusCode.Conflict
            val apiError = response.body<ApiError>()
            apiError.message shouldBe "Oppfolgingsplan is already shared with Veileder"
            coVerify(exactly = 0) {
                dokarkivServiceMock.arkiverOppfolgingsplan(any(), any())
            }
        }
    }

    it("POST /oppfolgingsplaner/{uuid}/del-med-veileder should respond with OK and update plan when authorized") {
        withTestApplication {
            // Arrange
            texasClientMock.defaultMocks(pidInnlogetBruker, clientId = environment.syfoOppfolgingsplanFrontendClientId)

            dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

            coEvery { pdfGenServiceMock.generatePdf(any()) } returns generatedPdfStandin

            val randomJournalpostId = UUID.randomUUID().toString()
            coEvery { dokarkivServiceMock.arkiverOppfolgingsplan(any(), any()) } returns randomJournalpostId

            val uuid = testDb.persistOppfolgingsplan(
                defaultPersistedOppfolgingsplan()
                    .copy(narmesteLederId = narmestelederId)
            )
            // Act
            val response = client.post {
                url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/$uuid/del-med-veileder")
                bearerAuth("Bearer token")
            }
            // Assert
            response.status shouldBe HttpStatusCode.OK
            val plan = testDb.findAllOppfolgingsplanerBy("12345678901", "orgnummer").first { it.uuid == uuid }
            plan.skalDelesMedVeileder shouldBe true
            plan.deltMedVeilederTidspunkt shouldNotBe null
            plan.journalpostId shouldBe randomJournalpostId
            coVerify(exactly = 1) {
                dokarkivServiceMock.arkiverOppfolgingsplan(any(), any())
            }
        }
    }

    it("POST /oppfolgingsplaner/{uuid}/del-med-veileder should respond with 500 when archiving fails") {
        withTestApplication {
            // Arrange
            texasClientMock.defaultMocks(pidInnlogetBruker, clientId = environment.syfoOppfolgingsplanFrontendClientId)

            dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

            coEvery { pdfGenServiceMock.generatePdf(any()) } returns generatedPdfStandin

            coEvery { dokarkivServiceMock.arkiverOppfolgingsplan(any(), any()) } throws Exception("exception")

            val uuid = testDb.persistOppfolgingsplan(
                defaultPersistedOppfolgingsplan()
                    .copy(narmesteLederId = narmestelederId)
            )
            // Act
            val response = client.post {
                url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/$uuid/del-med-veileder")
                bearerAuth("Bearer token")
            }
            // Assert
            response.status shouldBe HttpStatusCode.InternalServerError
            val plan = testDb.findAllOppfolgingsplanerBy("12345678901", "orgnummer").first { it.uuid == uuid }
            // When archiving fails, no updates should be made to the plan (transactional behavior)
            plan.skalDelesMedVeileder shouldBe false
            plan.deltMedVeilederTidspunkt shouldBe null
            plan.journalpostId shouldBe null
            coVerify(exactly = 1) {
                dokarkivServiceMock.arkiverOppfolgingsplan(any(), any())
            }
        }
    }
})
