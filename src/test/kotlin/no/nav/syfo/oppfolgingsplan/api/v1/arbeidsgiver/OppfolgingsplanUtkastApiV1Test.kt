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
import io.mockk.mockk
import java.time.LocalDate
import java.util.*
import no.nav.syfo.TestDB
import no.nav.syfo.defaultFormSnapshot
import no.nav.syfo.defaultMocks
import no.nav.syfo.defaultSykmeldt
import no.nav.syfo.defaultUtkast
import no.nav.syfo.dinesykmeldte.client.DineSykmeldteHttpClient
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.dokarkiv.DokarkivService
import no.nav.syfo.isdialogmelding.client.IsDialogmeldingClient
import no.nav.syfo.isdialogmelding.IsDialogmeldingService
import no.nav.syfo.istilgangskontroll.IsTilgangskontrollService
import no.nav.syfo.istilgangskontroll.client.IIsTilgangskontrollClient
import no.nav.syfo.oppfolgingsplan.api.v1.registerApiV1
import no.nav.syfo.oppfolgingsplan.db.PersistedOppfolgingsplanUtkast
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanUtkastBy
import no.nav.syfo.oppfolgingsplan.db.upsertOppfolgingsplanUtkast
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.pdfgen.client.PdfGenClient
import no.nav.syfo.pdfgen.PdfGenService
import no.nav.syfo.pdl.PdlService
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.plugins.installContentNegotiation
import no.nav.syfo.plugins.installStatusPages
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.varsel.EsyfovarselProducer

class OppfolgingsplanUtkastApiV1Test : DescribeSpec({

    val texasClientMock = mockk<TexasHttpClient>()
    val dineSykmeldteHttpClientMock = mockk<DineSykmeldteHttpClient>()
    val testDb = TestDB.database
    val esyfovarselProducerMock = mockk<EsyfovarselProducer>()
    val pdfGenClient = mockk<PdfGenClient>()
    val isDialogmeldingClientMock = mockk<IsDialogmeldingClient>()
    val isTilgangskontrollClientMock = mockk<IIsTilgangskontrollClient>()
    val dokarkivServiceMock = mockk<DokarkivService>()
    val isTilgangskontrollServiceMock = IsTilgangskontrollService(isTilgangskontrollClientMock)
    val pdlClientMock = mockk<PdlClient>()

    val narmestelederId = UUID.randomUUID().toString()
    val pidInnlogetBruker = "10987654321"
    val sykmeldt = defaultSykmeldt().copy(narmestelederId = narmestelederId)

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
                            esyfovarselProducer = esyfovarselProducerMock,
                        ),
                        pdfGenService = PdfGenService(
                            pdfGenClient,
                            testDb,
                            PdlService(
                                pdlClientMock,
                            )
                        ),
                        isDialogmeldingService = IsDialogmeldingService(isDialogmeldingClientMock),
                        dokarkivService = dokarkivServiceMock,
                        isTilgangskontrollService = isTilgangskontrollServiceMock,
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
                texasClientMock.defaultMocks(pidInnlogetBruker)

                dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

                val utkast = defaultUtkast()

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
                    it.evalueringsdato shouldBe utkast.evalueringsdato
                }
            }
        }

        it("PUT /oppfolgingsplaner/utkast overwrite existing draft") {
            withTestApplication {
                // Arrange
                texasClientMock.defaultMocks(pidInnlogetBruker)

                dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

                val existingUUID = testDb.upsertOppfolgingsplanUtkast(
                    narmesteLederFnr = pidInnlogetBruker,
                    sykmeldt = sykmeldt,
                    defaultUtkast()
                        .copy(
                            evalueringsdato = LocalDate.parse("2020-01-01")
                        )
                )

                // Act
                val response = client.put("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/utkast") {
                    bearerAuth("Bearer token")
                    contentType(ContentType.Application.Json)
                    setBody(
                        defaultUtkast().copy(
                            evalueringsdato = LocalDate.parse("2020-01-02")
                        )
                    )
                }

                // Assert
                response.status shouldBe HttpStatusCode.OK

                val persisted = testDb.findOppfolgingsplanUtkastBy("12345678901", "orgnummer")
                persisted shouldNotBe null
                persisted?.let {
                    it.uuid shouldBe existingUUID
                    it.sykmeldtFnr shouldBe sykmeldt.fnr
                    it.narmesteLederId shouldBe narmestelederId
                    it.narmesteLederFnr shouldBe pidInnlogetBruker
                    it.organisasjonsnummer shouldBe sykmeldt.orgnummer
                    it.content shouldBe defaultFormSnapshot()
                    it.evalueringsdato shouldBe LocalDate.parse("2020-01-02")
                }
            }
        }

        it("GET /oppfolgingsplaner/utkast should retrieve the current oppfolgingsplan utkast") {
            withTestApplication {
                // Arrange
                texasClientMock.defaultMocks(pidInnlogetBruker)

                dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

                val requestUtkast = defaultUtkast()
                val existingUUID = testDb.upsertOppfolgingsplanUtkast(
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
                val utkast = response.body<PersistedOppfolgingsplanUtkast>()
                utkast shouldNotBe null
                utkast.uuid shouldBe existingUUID
                utkast.sykmeldtFnr shouldBe sykmeldt.fnr
                utkast.narmesteLederFnr shouldBe pidInnlogetBruker
                utkast.organisasjonsnummer shouldBe sykmeldt.orgnummer
                utkast.content shouldBe defaultFormSnapshot()
                utkast.evalueringsdato shouldBe requestUtkast.evalueringsdato
            }
        }
    }
})
