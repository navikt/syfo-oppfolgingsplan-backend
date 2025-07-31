package no.nav.syfo.oppfolgingsplan.api.v1.arbeidsgiver

import no.nav.syfo.isdialogmelding.IsDialogmeldingClient
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
import io.mockk.mockk
import io.mockk.verify
import java.util.*
import no.nav.syfo.TestDB
import no.nav.syfo.application.exception.ApiError
import no.nav.syfo.defaultOppfolgingsplan
import no.nav.syfo.defaultSykmeldt
import no.nav.syfo.defaultUtkast
import no.nav.syfo.dinesykmeldte.DineSykmeldteHttpClient
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.generatedPdfStandin
import no.nav.syfo.isdialogmelding.IsDialogmeldingService
import no.nav.syfo.oppfolgingsplan.api.v1.registerApiV1
import no.nav.syfo.oppfolgingsplan.db.PersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.db.findAllOppfolgingsplanerBy
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanUtkastBy
import no.nav.syfo.oppfolgingsplan.db.upsertOppfolgingsplanUtkast
import no.nav.syfo.oppfolgingsplan.dto.OppfolgingsplanOverview
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.pdfgen.PdfGenService
import no.nav.syfo.persistOppfolgingsplan
import no.nav.syfo.plugins.installContentNegotiation
import no.nav.syfo.plugins.installStatusPages
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
    val narmestelederId = UUID.randomUUID().toString()
    val isDialogmeldingClientMock = mockk<IsDialogmeldingClient>()
    val pdfGenServiceMock = mockk<PdfGenService>()


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
                installStatusPages()
                routing {
                    registerApiV1(
                        DineSykmeldteService(dineSykmeldteHttpClientMock),
                        texasClientMock,
                        oppfolgingsplanService = OppfolgingsplanService(
                            database = testDb,
                            esyfovarselProducer = esyfovarselProducerMock
                        ),
                        pdfGenService = pdfGenServiceMock,
                        isDialogmeldingService = IsDialogmeldingService(isDialogmeldingClientMock)
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
        it("GET /oppfolgingsplaner/oversikt should respond with OK when texas client gives active response") {
            withTestApplication {
                // Arrange
                coEvery {
                    texasClientMock.introspectToken(any(), any())
                } returns TexasIntrospectionResponse(active = true, pid = "userIdentifier", acr = "Level4")

                coEvery {
                    texasClientMock.exchangeTokenForDineSykmeldte(any())
                } returns TexasExchangeResponse("token", 111, "tokenType")

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
                coEvery {
                    texasClientMock.introspectToken(any(), any())
                } returns TexasIntrospectionResponse(active = true, pid = "userIdentifier", acr = "Level3")

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
                } returns TexasIntrospectionResponse(active = false, sub = "user")

                // Act
                val response = client.get {
                    url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/oversikt")
                    bearerAuth("Bearer token")
                }

                // Assert
                response.status shouldBe HttpStatusCode.Unauthorized
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

                coEvery {
                    dineSykmeldteHttpClientMock.getSykmeldtForNarmesteLederId(
                        narmestelederId,
                        "token"
                    )
                } returns defaultSykmeldt().copy(narmestelederId = narmestelederId)

                // Act
                val response = client.get {
                    url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/00000000-0000-0000-0000-000000000000")
                    bearerAuth("Bearer token")
                }

                // Assert
                response.status shouldBe HttpStatusCode.NotFound
            }
        }

        it("GET /oppfolgingsplaner/{uuid} should respond with OK and return oppfolgingsplan when found and authorized") {
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

                coEvery {
                    dineSykmeldteHttpClientMock.getSykmeldtForNarmesteLederId(
                        narmestelederId,
                        "token"
                    )
                } returns defaultSykmeldt().copy(narmestelederId = narmestelederId)

                val existingUUID = testDb.persistOppfolgingsplan(
                    narmesteLederId = narmestelederId,
                    createOppfolgingsplanRequest = defaultOppfolgingsplan()
                )

                // Act
                val response = client.get {
                    url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/$existingUUID")
                    bearerAuth("Bearer token")
                }

                // Assert
                response.status shouldBe HttpStatusCode.OK
                val plan = response.body<PersistedOppfolgingsplan>()
                plan.uuid shouldBe existingUUID
            }
        }

        it("GET /oppfolgingsplaner/oversikt should respond with OK and return overview") {
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

                coEvery {
                    dineSykmeldteHttpClientMock.getSykmeldtForNarmesteLederId(
                        narmestelederId,
                        "token"
                    )
                } returns defaultSykmeldt().copy(narmestelederId = narmestelederId)

                val firstPlanUUID = testDb.persistOppfolgingsplan(
                    narmesteLederId = narmestelederId,
                    createOppfolgingsplanRequest = defaultOppfolgingsplan()
                )
                val latestPlanUUID = testDb.persistOppfolgingsplan(
                    narmesteLederId = narmestelederId,
                    createOppfolgingsplanRequest = defaultOppfolgingsplan()
                )
                val utkastUUID = testDb.upsertOppfolgingsplanUtkast(
                    narmesteLederId = narmestelederId,
                    createUtkastRequest = defaultUtkast()
                )

                // Act
                val response = client.get {
                    url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/oversikt")
                    bearerAuth("Bearer token")
                }

                // Assert
                response.status shouldBe HttpStatusCode.OK
                val overview = response.body<OppfolgingsplanOverview>()
                overview.utkast?.uuid shouldBe utkastUUID
                overview.oppfolgingsplan?.uuid shouldBe latestPlanUUID
                overview.previousOppfolgingsplaner.size shouldBe 1
                overview.previousOppfolgingsplaner.first().uuid shouldBe firstPlanUUID
            }
        }
        it("POST /oppfolgingsplaner should respond with 201 when oppfolgingsplan is created successfully") {
            withTestApplication {
                // Arrange
                coEvery {
                    texasClientMock.introspectToken(any(), any())
                } returns TexasIntrospectionResponse(active = true, pid = "userIdentifier", acr = "Level4")

                coEvery {
                    texasClientMock.exchangeTokenForDineSykmeldte(any())
                } returns TexasExchangeResponse("token", 111, "tokenType")

                coEvery {
                    dineSykmeldteHttpClientMock.getSykmeldtForNarmesteLederId(narmestelederId, "token")
                } returns defaultSykmeldt().copy(narmestelederId = narmestelederId)

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
                persisted.first().sykmeldtFnr shouldBe oppfolgingsplan.sykmeldtFnr
                persisted.first().narmesteLederFnr shouldBe oppfolgingsplan.narmesteLederFnr
                persisted.first().narmesteLederId shouldBe narmestelederId
                persisted.first().orgnummer shouldBe oppfolgingsplan.orgnummer
                persisted.first().content.toString() shouldBe oppfolgingsplan.content.toString()
                persisted.first().sluttdato.toString() shouldBe oppfolgingsplan.sluttdato.toString()
                persisted.first().skalDelesMedLege shouldBe oppfolgingsplan.skalDelesMedLege
                persisted.first().skalDelesMedVeileder shouldBe oppfolgingsplan.skalDelesMedVeileder
                persisted.first().deltMedVeilederTidspunkt shouldBe oppfolgingsplan.deltMedVeilederTidspunkt
                persisted.first().deltMedLegeTidspunkt shouldBe oppfolgingsplan.deltMedLegeTidspunkt
                verify(exactly = 1) {
                    esyfovarselProducerMock.sendVarselToEsyfovarsel(withArg {
                        val hendelse = it as ArbeidstakerHendelse
                        hendelse.arbeidstakerFnr shouldBe oppfolgingsplan.sykmeldtFnr
                        hendelse.orgnummer shouldBe oppfolgingsplan.orgnummer
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
                    texasClientMock.exchangeTokenForDineSykmeldte(any())
                } returns TexasExchangeResponse("token", 111, "tokenType")

                coEvery {
                    dineSykmeldteHttpClientMock.getSykmeldtForNarmesteLederId(narmestelederId, "token")
                } returns defaultSykmeldt().copy(narmestelederId = narmestelederId)

                coEvery {
                    esyfovarselProducerMock.sendVarselToEsyfovarsel(any())
                } returns Unit

                testDb.upsertOppfolgingsplanUtkast(
                    narmestelederId,
                    defaultUtkast()
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
                        hendelse.arbeidstakerFnr shouldBe oppfolgingsplan.sykmeldtFnr
                        hendelse.orgnummer shouldBe oppfolgingsplan.orgnummer
                    })
                }
            }
        }
    }
    it("POST /oppfolgingsplaner still creates new oppfolgingsplan when kafka producer throws exception") {
        withTestApplication {
            // Arrange
            coEvery {
                texasClientMock.introspectToken(any(), any())
            } returns TexasIntrospectionResponse(active = true, pid = "userIdentifier", acr = "Level4")

            coEvery {
                texasClientMock.exchangeTokenForDineSykmeldte(any())
            } returns TexasExchangeResponse("token", 111, "tokenType")

            coEvery {
                dineSykmeldteHttpClientMock.getSykmeldtForNarmesteLederId(narmestelederId, "token")
            } returns defaultSykmeldt().copy(narmestelederId = narmestelederId)

            coEvery {
                esyfovarselProducerMock.sendVarselToEsyfovarsel(any())
            } throws Exception("exception")

            testDb.upsertOppfolgingsplanUtkast(
                narmestelederId,
                defaultUtkast()
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
                    hendelse.arbeidstakerFnr shouldBe oppfolgingsplan.sykmeldtFnr
                    hendelse.orgnummer shouldBe oppfolgingsplan.orgnummer
                })
            }
        }
    }
    it("POST /oppfolgingsplaner/{uuid}/del-med-lege should respond with NotFound if plan does not exist") {
        withTestApplication {
            // Arrange
            coEvery { texasClientMock.introspectToken(any(), any()) } returns TexasIntrospectionResponse(
                active = true,
                pid = "user",
                acr = "Level4"
            )
            coEvery {
                texasClientMock.exchangeTokenForDineSykmeldte(any())
            } returns TexasExchangeResponse("token", 111, "tokenType")
            coEvery {
                texasClientMock.exchangeTokenForIsDialogmelding(any())
            } returns TexasExchangeResponse(
                "token",
                111,
                "tokenType"
            )
            coEvery {
                dineSykmeldteHttpClientMock.getSykmeldtForNarmesteLederId(
                    narmestelederId,
                    "token"
                )
            } returns defaultSykmeldt()
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
        }
    }

    it("POST /oppfolgingsplaner/{uuid}/del-med-lege should respond with OK and update plan when authorized") {
        withTestApplication {
            // Arrange
            coEvery { texasClientMock.introspectToken(any(), any()) } returns TexasIntrospectionResponse(
                active = true,
                pid = "user",
                acr = "Level4"
            )
            coEvery {
                texasClientMock.exchangeTokenForDineSykmeldte(any())
            } returns TexasExchangeResponse("token", 111, "tokenType")
            coEvery {
                texasClientMock.exchangeTokenForIsDialogmelding(any())
            } returns TexasExchangeResponse(
                "token",
                111,
                "tokenType"
            )
            coEvery {
                dineSykmeldteHttpClientMock.getSykmeldtForNarmesteLederId(
                    narmestelederId,
                    "token"
                )
            } returns defaultSykmeldt()
            coEvery { pdfGenServiceMock.generatePdf(any()) } returns generatedPdfStandin
            coEvery {
                isDialogmeldingClientMock.sendLpsPlanToGeneralPractitioner(
                    any(),
                    any(),
                    any()
                )
            } returns Unit
            val uuid = testDb.persistOppfolgingsplan(
                narmesteLederId = narmestelederId,
                createOppfolgingsplanRequest = defaultOppfolgingsplan()
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
})
