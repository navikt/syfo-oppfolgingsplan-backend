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
import io.ktor.client.request.delete
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
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.TestDB
import no.nav.syfo.application.Environment
import no.nav.syfo.application.LocalEnvironment
import no.nav.syfo.application.valkey.ValkeyCache
import no.nav.syfo.defaultMocks
import no.nav.syfo.defaultSykmeldt
import no.nav.syfo.defaultUtkastRequest
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.dinesykmeldte.client.DineSykmeldteHttpClient
import no.nav.syfo.dokarkiv.DokarkivService
import no.nav.syfo.isdialogmelding.IsDialogmeldingService
import no.nav.syfo.isdialogmelding.client.IsDialogmeldingClient
import no.nav.syfo.istilgangskontroll.IsTilgangskontrollService
import no.nav.syfo.istilgangskontroll.client.IIsTilgangskontrollClient
import no.nav.syfo.oppfolgingsplan.api.v1.registerApiV1
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanUtkastBy
import no.nav.syfo.oppfolgingsplan.db.upsertOppfolgingsplanUtkast
import no.nav.syfo.oppfolgingsplan.dto.OppfolgingsplanUtkastResponse
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.pdfgen.PdfGenService
import no.nav.syfo.pdfgen.client.PdfGenClient
import no.nav.syfo.pdl.PdlService
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.plugins.installContentNegotiation
import no.nav.syfo.plugins.installStatusPages
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.varsel.EsyfovarselProducer
import java.util.*

class OppfolgingsplanUtkastApiV1Test : DescribeSpec({

    val texasClientMock = mockk<TexasHttpClient>()
    val dineSykmeldteHttpClientMock = mockk<DineSykmeldteHttpClient>()
    val valkeyCacheMock = mockk<ValkeyCache>(relaxUnitFun = true)
    val testDb = TestDB.database
    val esyfovarselProducerMock = mockk<EsyfovarselProducer>()
    val pdfGenClient = mockk<PdfGenClient>()
    val isDialogmeldingClientMock = mockk<IsDialogmeldingClient>()
    val isTilgangskontrollClientMock = mockk<IIsTilgangskontrollClient>()
    val dokarkivServiceMock = mockk<DokarkivService>()
    val isTilgangskontrollServiceMock = IsTilgangskontrollService(isTilgangskontrollClientMock)
    val pdlClientMock = mockk<PdlClient>()
    val pdlService = PdlService(pdlClientMock)
    val oppfolgingsplanService = OppfolgingsplanService(
        database = testDb,
        esyfovarselProducer = esyfovarselProducerMock,
        pdlService = pdlService,
    )
    val narmestelederId = UUID.randomUUID().toString()
    val pidInnlogetBruker = "10987654321"
    val sykmeldt = defaultSykmeldt().copy(narmestelederId = narmestelederId)
    val environment: Environment = LocalEnvironment()

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
                        oppfolgingsplanService = oppfolgingsplanService,
                        pdfGenService = PdfGenService(pdfGenClient, oppfolgingsplanService),
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
    describe("Oppfolgingsplan Utkast API V1") {
        it("PUT /oppfolgingsplaner/utkast creates a new draft if it does not exist") {
            withTestApplication {
                // Arrange
                texasClientMock.defaultMocks(pidInnlogetBruker, azp = environment.syfoOppfolgingsplanFrontendClientId)

                dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

                val utkast = defaultUtkastRequest()

                // Act
                val response = client.put("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/utkast") {
                    bearerAuth("Bearer token")
                    contentType(ContentType.Application.Json)
                    setBody(utkast)
                }

                // Assert
                response.status shouldBe HttpStatusCode.OK

                val persisted = testDb.findOppfolgingsplanUtkastBy("12345678901", "orgnummer")
                persisted shouldNotBe null
                persisted?.let {
                    it.sykmeldtFnr shouldBe "12345678901"
                    it.narmesteLederId shouldBe narmestelederId
                    it.narmesteLederFnr shouldBe pidInnlogetBruker
                    it.organisasjonsnummer shouldBe "orgnummer"
                    it.content shouldNotBe null
                }
            }
        }

        it("PUT /oppfolgingsplaner/utkast overwrite existing draft") {
            withTestApplication {
                texasClientMock.defaultMocks(pidInnlogetBruker, azp = environment.syfoOppfolgingsplanFrontendClientId)

                dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

                val initialUtkastRequest = defaultUtkastRequest { put("hvordanFolgeOpp", "initial value") }
                val updatedUtkastRequest = defaultUtkastRequest { put("hvordanFolgeOpp", "updated value") }

                val existingUUID = testDb.upsertOppfolgingsplanUtkast(
                    narmesteLederFnr = pidInnlogetBruker,
                    sykmeldt = sykmeldt,
                    initialUtkastRequest
                )

                val initialPersistedUtkast = testDb.findOppfolgingsplanUtkastBy("12345678901", "orgnummer")!!
                initialPersistedUtkast.content shouldBe initialUtkastRequest.content
                initialPersistedUtkast.content["hvordanFolgeOpp"] shouldBe "initial value"

                val response = client.put("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/utkast") {
                    bearerAuth("Bearer token")
                    contentType(ContentType.Application.Json)
                    setBody(updatedUtkastRequest)
                }

                response.status shouldBe HttpStatusCode.OK

                val updatedPersistedUtkast = testDb.findOppfolgingsplanUtkastBy("12345678901", "orgnummer")!!
                updatedPersistedUtkast shouldNotBe null
                updatedPersistedUtkast.uuid shouldBe existingUUID
                updatedPersistedUtkast.sykmeldtFnr shouldBe sykmeldt.fnr
                updatedPersistedUtkast.narmesteLederId shouldBe narmestelederId
                updatedPersistedUtkast.narmesteLederFnr shouldBe pidInnlogetBruker
                updatedPersistedUtkast.organisasjonsnummer shouldBe sykmeldt.orgnummer
                updatedPersistedUtkast.content shouldBe updatedUtkastRequest.content
                updatedPersistedUtkast.content["hvordanFolgeOpp"] shouldBe "updated value"
            }
        }

        it("GET /oppfolgingsplaner/utkast should retrieve the current oppfolgingsplan utkast") {
            withTestApplication {
                // Arrange
                texasClientMock.defaultMocks(pidInnlogetBruker, azp = environment.syfoOppfolgingsplanFrontendClientId)

                dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

                val requestUtkast = defaultUtkastRequest()
                testDb.upsertOppfolgingsplanUtkast(
                    narmesteLederFnr = pidInnlogetBruker,
                    sykmeldt = sykmeldt,
                    requestUtkast
                )

                // Act
                val response = client.get("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/utkast") {
                    bearerAuth("Bearer token")
                }

                // Assert
                response.status shouldBe HttpStatusCode.OK
                val utkastResponse = response.body<OppfolgingsplanUtkastResponse>()
                utkastResponse shouldNotBe null
                utkastResponse.utkast?.content shouldBe requestUtkast.content
            }
        }

        it("PUT /oppfolgingsplaner/utkast should handle null values correctly") {
            withTestApplication {
                texasClientMock.defaultMocks(pidInnlogetBruker, azp = environment.syfoOppfolgingsplanFrontendClientId)
                dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

                val utkastWithNulls = defaultUtkastRequest {
                    put("evalueringsDato", null)
                    put("harDenAnsatteMedvirket", null)
                    put("arbeidsoppgaverSomIkkeKanUtfores", "")
                    put("tidligereTilrettelegging", "")
                }

                val response = client.put("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/utkast") {
                    bearerAuth("Bearer token")
                    contentType(ContentType.Application.Json)
                    setBody(utkastWithNulls)
                }

                response.status shouldBe HttpStatusCode.OK

                val persisted = testDb.findOppfolgingsplanUtkastBy("12345678901", "orgnummer")!!
                persisted.content shouldBe utkastWithNulls.content
                persisted.content["evalueringsDato"] shouldBe null
                persisted.content["harDenAnsatteMedvirket"] shouldBe null
                persisted.content["arbeidsoppgaverSomIkkeKanUtfores"] shouldBe ""
                persisted.content["tidligereTilrettelegging"] shouldBe ""
                persisted.content["typiskArbeidshverdag"] shouldBe "Dette skrev jeg forrige gang. Kjekt at det blir lagret i et utkast."
            }
        }

        it("DELETE /oppfolgingsplaner/utkast should delete existing draft") {
            withTestApplication {
                // Arrange
                texasClientMock.defaultMocks(pidInnlogetBruker, azp = environment.syfoOppfolgingsplanFrontendClientId)
                dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

                val utkast = defaultUtkastRequest()
                testDb.upsertOppfolgingsplanUtkast(
                    narmesteLederFnr = pidInnlogetBruker,
                    sykmeldt = sykmeldt,
                    utkast
                )

                // Verify draft exists
                val existingUtkast = testDb.findOppfolgingsplanUtkastBy("12345678901", "orgnummer")
                existingUtkast shouldNotBe null

                // Act
                val response = client.delete("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/utkast") {
                    bearerAuth("Bearer token")
                }

                // Assert
                response.status shouldBe HttpStatusCode.NoContent

                val deletedUtkast = testDb.findOppfolgingsplanUtkastBy("12345678901", "orgnummer")
                deletedUtkast shouldBe null
            }
        }

        it("DELETE /oppfolgingsplaner/utkast should return 400 when sykmeldt has no active sykmelding") {
            withTestApplication {
                // Arrange
                texasClientMock.defaultMocks(pidInnlogetBruker, azp = environment.syfoOppfolgingsplanFrontendClientId)

                val sykmeldtWithoutActiveSykmelding = sykmeldt.copy(aktivSykmelding = false)
                coEvery {
                    dineSykmeldteHttpClientMock.getSykmeldtForNarmesteLederId(
                        narmestelederId,
                        "token"
                    )
                } returns sykmeldtWithoutActiveSykmelding

                val utkast = defaultUtkastRequest()
                testDb.upsertOppfolgingsplanUtkast(
                    narmesteLederFnr = pidInnlogetBruker,
                    sykmeldt = sykmeldtWithoutActiveSykmelding,
                    utkast
                )

                // Act
                val response = client.delete("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/utkast") {
                    bearerAuth("Bearer token")
                }

                // Assert
                response.status shouldBe HttpStatusCode.BadRequest
            }
        }
    }
})
