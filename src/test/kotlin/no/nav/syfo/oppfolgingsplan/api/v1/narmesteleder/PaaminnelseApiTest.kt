package no.nav.syfo.oppfolgingsplan.api.v1.narmesteleder

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.http.HttpStatusCode
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
import no.nav.syfo.application.exception.ApiError
import no.nav.syfo.application.exception.ErrorType
import no.nav.syfo.application.valkey.ValkeyCache
import no.nav.syfo.defaultMocks
import no.nav.syfo.defaultSykmeldt
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.dinesykmeldte.client.DineSykmeldteHttpClient
import no.nav.syfo.oppfolgingsplan.db.findPaaminnelseBy
import no.nav.syfo.oppfolgingsplan.dto.PaaminnelseStatus
import no.nav.syfo.oppfolgingsplan.dto.PaaminnelseStatusDto
import no.nav.syfo.oppfolgingsplan.service.PaaminnelseService
import no.nav.syfo.plugins.installContentNegotiation
import no.nav.syfo.plugins.installStatusPages
import no.nav.syfo.returnsNotFound
import no.nav.syfo.sykmelding.db.SykmeldingsperiodeRepository
import no.nav.syfo.sykmelding.db.domain.SykmeldingsperiodeToStore
import no.nav.syfo.texas.client.TexasHttpClient
import java.time.LocalDate

class PaaminnelseApiTest :
    DescribeSpec({
        val texasClientMock = mockk<TexasHttpClient>()
        val dineSykmeldteHttpClientMock = mockk<DineSykmeldteHttpClient>()
        val valkeyCacheMock = mockk<ValkeyCache>(relaxUnitFun = true)
        val testDb = TestDB.database
        val repository = SykmeldingsperiodeRepository(testDb)
        val environment: Environment = LocalEnvironment()
        val paaminnelseService = PaaminnelseService(
            database = testDb,
            sykmeldingsperiodeRepository = repository,
        )

        val narmestelederId = "2c1d58cc-a4cc-48a6-a0ef-a0eb0b05f45d"
        val pidInnloggetBruker = "10987654321"

        beforeTest {
            clearAllMocks()
            TestDB.clearAllData()
            every { valkeyCacheMock.getSykmeldt(any(), any()) } returns null
        }

        fun seedAktivtSyketilfelle(startDato: LocalDate = LocalDate.now().minusDays(7)) {
            repository.storeSykmeldingsperioder(
                listOf(
                    SykmeldingsperiodeToStore(
                        sykmeldtFnr = "12345678901",
                        organisasjonsnummer = "orgnummer",
                        sykmeldingId = "sykmelding-1",
                        fom = startDato,
                        tom = startDato.plusDays(14),
                    ),
                ),
            )
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
                        registerPaaminnelseApi(
                            dineSykmeldteService = DineSykmeldteService(dineSykmeldteHttpClientMock, valkeyCacheMock),
                            texasHttpClient = texasClientMock,
                            paaminnelseService = paaminnelseService,
                            environment = environment,
                        )
                    }
                }
                fn(this)
            }
        }

        fun countPaaminnelseRows(): Int = testDb.connection.use { connection ->
            connection.prepareStatement("SELECT COUNT(*) AS count FROM paaminnelse").use { preparedStatement ->
                preparedStatement.executeQuery().use { resultSet ->
                    resultSet.next()
                    resultSet.getInt("count")
                }
            }
        }

        describe("Paaminnelse API") {
            it("GET should respond with Unauthorized when no authentication is provided") {
                withTestApplication {
                    val response = client.get("/api/v1/narmesteleder/$narmestelederId/oppfolgingsplaner/paaminnelse")

                    response.status shouldBe HttpStatusCode.Unauthorized
                }
            }

            it("GET should respond with SYKMELDT_NOT_FOUND when dine sykmeldte returns not found") {
                withTestApplication {
                    texasClientMock.defaultMocks(
                        pid = pidInnloggetBruker,
                        clientId = environment.dinesykmeldteBackendClientId,
                    )
                    dineSykmeldteHttpClientMock.returnsNotFound(narmestelederId = narmestelederId)

                    val response = client.get {
                        url("/api/v1/narmesteleder/$narmestelederId/oppfolgingsplaner/paaminnelse")
                        bearerAuth("Bearer token")
                    }

                    response.status shouldBe HttpStatusCode.NotFound
                    response.body<ApiError>().type shouldBe ErrorType.SYKMELDT_NOT_FOUND
                }
            }

            it("GET should return TILGJENGELIG when relation is inside the ordering window and not ordered") {
                withTestApplication {
                    val startDato = LocalDate.now().minusDays(7)
                    seedAktivtSyketilfelle(startDato)
                    texasClientMock.defaultMocks(
                        pid = pidInnloggetBruker,
                        clientId = environment.dinesykmeldteBackendClientId,
                    )
                    dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

                    val response = client.get {
                        url("/api/v1/narmesteleder/$narmestelederId/oppfolgingsplaner/paaminnelse")
                        bearerAuth("Bearer token")
                    }

                    response.status shouldBe HttpStatusCode.OK
                    response.body<PaaminnelseStatusDto>() shouldBe PaaminnelseStatusDto(
                        status = PaaminnelseStatus.TILGJENGELIG,
                        synligFra = startDato,
                    )
                }
            }

            it("POST should persist BESTILT") {
                withTestApplication {
                    seedAktivtSyketilfelle()
                    texasClientMock.defaultMocks(
                        pid = pidInnloggetBruker,
                        clientId = environment.dinesykmeldteBackendClientId,
                    )
                    dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

                    val response = client.post {
                        url("/api/v1/narmesteleder/$narmestelederId/oppfolgingsplaner/paaminnelse")
                        bearerAuth("Bearer token")
                    }

                    response.status shouldBe HttpStatusCode.OK
                    response.body<PaaminnelseStatusDto>().status shouldBe PaaminnelseStatus.BESTILT

                    val persisted = testDb.findPaaminnelseBy("12345678901", "orgnummer")
                    persisted?.bestilt shouldBe true
                    persisted?.sykmeldtFnr shouldBe "12345678901"
                    persisted?.organisasjonsnummer shouldBe "orgnummer"
                }
            }

            it("POST should respond with BadRequest when sykmeldt has no active sykmelding") {
                withTestApplication {
                    texasClientMock.defaultMocks(
                        pid = pidInnloggetBruker,
                        clientId = environment.dinesykmeldteBackendClientId,
                    )
                    coEvery {
                        dineSykmeldteHttpClientMock.getSykmeldtForNarmesteLederId(
                            narmestelederId,
                            "token",
                        )
                    } returns defaultSykmeldt().copy(
                        narmestelederId = narmestelederId,
                        aktivSykmelding = false,
                    )

                    val response = client.post {
                        url("/api/v1/narmesteleder/$narmestelederId/oppfolgingsplaner/paaminnelse")
                        bearerAuth("Bearer token")
                    }

                    response.status shouldBe HttpStatusCode.BadRequest
                    response.body<ApiError>().type shouldBe ErrorType.BAD_REQUEST
                    countPaaminnelseRows() shouldBe 0
                }
            }

            it("POST should be idempotent") {
                withTestApplication {
                    seedAktivtSyketilfelle()
                    texasClientMock.defaultMocks(
                        pid = pidInnloggetBruker,
                        clientId = environment.dinesykmeldteBackendClientId,
                    )
                    dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

                    client.post {
                        url("/api/v1/narmesteleder/$narmestelederId/oppfolgingsplaner/paaminnelse")
                        bearerAuth("Bearer token")
                    }
                    val response = client.post {
                        url("/api/v1/narmesteleder/$narmestelederId/oppfolgingsplaner/paaminnelse")
                        bearerAuth("Bearer token")
                    }

                    response.status shouldBe HttpStatusCode.OK
                    countPaaminnelseRows() shouldBe 1
                }
            }

            it("DELETE should persist TILGJENGELIG") {
                withTestApplication {
                    seedAktivtSyketilfelle()
                    texasClientMock.defaultMocks(
                        pid = pidInnloggetBruker,
                        clientId = environment.dinesykmeldteBackendClientId,
                    )
                    dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

                    client.post {
                        url("/api/v1/narmesteleder/$narmestelederId/oppfolgingsplaner/paaminnelse")
                        bearerAuth("Bearer token")
                    }
                    val response = client.delete {
                        url("/api/v1/narmesteleder/$narmestelederId/oppfolgingsplaner/paaminnelse")
                        bearerAuth("Bearer token")
                    }

                    response.status shouldBe HttpStatusCode.OK
                    response.body<PaaminnelseStatusDto>().status shouldBe PaaminnelseStatus.TILGJENGELIG
                    testDb.findPaaminnelseBy("12345678901", "orgnummer")?.bestilt shouldBe false
                }
            }
        }
    })
