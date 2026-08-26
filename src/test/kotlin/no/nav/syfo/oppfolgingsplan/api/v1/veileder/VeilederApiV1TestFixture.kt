package no.nav.syfo.oppfolgingsplan.api.v1.veileder

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.TestDB
import no.nav.syfo.aareg.AaregService
import no.nav.syfo.application.Environment
import no.nav.syfo.application.LocalEnvironment
import no.nav.syfo.application.valkey.ValkeyCache
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.dinesykmeldte.client.DineSykmeldteHttpClient
import no.nav.syfo.dokarkiv.DokarkivService
import no.nav.syfo.isdialogmelding.IsDialogmeldingService
import no.nav.syfo.isdialogmelding.client.IsDialogmeldingClient
import no.nav.syfo.istilgangskontroll.IsTilgangskontrollService
import no.nav.syfo.istilgangskontroll.client.IIsTilgangskontrollClient
import no.nav.syfo.oppfolgingsplan.api.v1.registerApiV1
import no.nav.syfo.oppfolgingsplan.db.OppfolgingsplanFinalizationRepository
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.oppfolgingsplan.service.UnntaksvurderingService
import no.nav.syfo.pdfgen.PdfGenService
import no.nav.syfo.plugins.installContentNegotiation
import no.nav.syfo.plugins.installStatusPages
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.varsel.EsyfovarselProducer
import java.util.UUID

class VeilederApiV1TestFixture {
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

    fun reset() {
        clearAllMocks(currentThreadOnly = true)
        TestDB.clearAllData()
        every { valkeyCacheMock.getSykmeldt(any(), any()) } returns null
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
                            pdlService = mockk(relaxed = true),
                            aaregService = mockk<AaregService>(relaxed = true),
                            unntaksvurderingService = mockk(relaxed = true),
                            oppfolgingsplanFinalizationRepository =
                            OppfolgingsplanFinalizationRepository(testDb),
                        ),
                        unntaksvurderingService = UnntaksvurderingService(testDb, mockk(relaxed = true)),
                        pdfGenService = pdfGenService,
                        isDialogmeldingService = IsDialogmeldingService(isDialogmeldingClientMock),
                        dokarkivService = dokarkivServiceMock,
                        isTilgangskontrollService = isTilgangskontrollServiceMock,
                        environment = this@VeilederApiV1TestFixture.environment,
                    )
                }
            }
            fn(this)
        }
    }
}
