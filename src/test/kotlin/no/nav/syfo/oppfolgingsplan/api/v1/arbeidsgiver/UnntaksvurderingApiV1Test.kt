package no.nav.syfo.oppfolgingsplan.api.v1.arbeidsgiver

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
import no.nav.syfo.aareg.AaregService
import no.nav.syfo.application.Environment
import no.nav.syfo.application.LocalEnvironment
import no.nav.syfo.application.valkey.ValkeyCache
import no.nav.syfo.defaultMocks
import no.nav.syfo.defaultPersistedOppfolgingsplan
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
import no.nav.syfo.oppfolgingsplan.db.findAllUnntaksvurderingerBy
import no.nav.syfo.oppfolgingsplan.db.persistUnntaksvurdering
import no.nav.syfo.oppfolgingsplan.db.upsertOppfolgingsplanUtkast
import no.nav.syfo.oppfolgingsplan.dto.ArbeidsgiverOppfolgingsplanOverviewResponse
import no.nav.syfo.oppfolgingsplan.dto.GjeldendeStatus
import no.nav.syfo.oppfolgingsplan.dto.MeldtAvRolle
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.oppfolgingsplan.service.UnntaksvurderingService
import no.nav.syfo.pdfgen.PdfGenService
import no.nav.syfo.pdl.PdlService
import no.nav.syfo.persistOppfolgingsplan
import no.nav.syfo.plugins.installContentNegotiation
import no.nav.syfo.plugins.installStatusPages
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.varsel.EsyfovarselProducer
import java.time.Instant
import java.util.UUID

class UnntaksvurderingApiV1Test :
    DescribeSpec({

        val texasClientMock = mockk<TexasHttpClient>()
        val dineSykmeldteHttpClientMock = mockk<DineSykmeldteHttpClient>()
        val valkeyCacheMock = mockk<ValkeyCache>(relaxUnitFun = true)
        val esyfovarselProducerMock = mockk<EsyfovarselProducer>()
        val testDb = TestDB.database
        val isDialogmeldingClientMock = mockk<IsDialogmeldingClient>()
        val isTilgangskontrollClientMock = mockk<IIsTilgangskontrollClient>()
        val pdfGenServiceMock = mockk<PdfGenService>()
        val pdlServiceMock = mockk<PdlService>()
        val aaregServiceMock = mockk<AaregService>()

        val narmestelederId = UUID.randomUUID().toString()
        val pidInnlogetBruker = "10987654321"
        val sykmeldt = defaultSykmeldt().copy(narmestelederId = narmestelederId)

        val dokarkivServiceMock = mockk<DokarkivService>()
        val isTilgangskontrollServiceMock = IsTilgangskontrollService(isTilgangskontrollClientMock)

        beforeTest {
            clearAllMocks(currentThreadOnly = true)
            TestDB.clearAllData()
            every { valkeyCacheMock.getSykmeldt(any(), any()) } returns null
            coEvery { pdlServiceMock.getNameFor(any()) } returns null
        }
        val unntaksvurderingService = UnntaksvurderingService(testDb, pdlServiceMock)
        val oppfolgingsplanService = OppfolgingsplanService(
            database = testDb,
            esyfovarselProducer = esyfovarselProducerMock,
            pdlService = pdlServiceMock,
            aaregService = aaregServiceMock,
            unntaksvurderingService = unntaksvurderingService,
        )
        val environment: Environment = LocalEnvironment()

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
                            oppfolgingsplanService = oppfolgingsplanService,
                            unntaksvurderingService = unntaksvurderingService,
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

        describe("GET /oppfolgingsplaner/oversikt") {
            it("returns empty unntaksvurderinger and status INGEN when nothing exists") {
                withTestApplication {
                    texasClientMock.defaultMocks(clientId = environment.syfoOppfolgingsplanFrontendClientId)
                    dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

                    val response = client.get {
                        url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/oversikt")
                        bearerAuth("Bearer token")
                    }

                    response.status shouldBe HttpStatusCode.OK
                    val overview = response.body<ArbeidsgiverOppfolgingsplanOverviewResponse>().oversikt
                    overview.unntaksvurderinger shouldBe emptyList()
                    overview.gjeldendeStatus shouldBe GjeldendeStatus.INGEN
                }
            }

            it("returns unntaksvurderinger newest first with status IKKE_AKTUELT, and does not touch tidligerePlaner") {
                withTestApplication {
                    texasClientMock.defaultMocks(clientId = environment.syfoOppfolgingsplanFrontendClientId)
                    dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

                    val first = testDb.persistUnntaksvurdering(pidInnlogetBruker, sykmeldt, "Maren Hegna")
                    val second = testDb.persistUnntaksvurdering(pidInnlogetBruker, sykmeldt, "Maren Hegna")

                    val response = client.get {
                        url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/oversikt")
                        bearerAuth("Bearer token")
                    }

                    response.status shouldBe HttpStatusCode.OK
                    val overview = response.body<ArbeidsgiverOppfolgingsplanOverviewResponse>().oversikt
                    overview.unntaksvurderinger.map { it.id } shouldBe listOf(second, first)
                    overview.unntaksvurderinger.first().meldtAv.navn shouldBe "Maren Hegna"
                    overview.unntaksvurderinger.first().meldtAv.rolle shouldBe MeldtAvRolle.ARBEIDSGIVER
                    overview.unntaksvurderinger.first().organization.orgNumber shouldBe sykmeldt.orgnummer
                    overview.unntaksvurderinger.first().organization.orgName shouldBe "Test AS"
                    overview.gjeldendeStatus shouldBe GjeldendeStatus.IKKE_AKTUELT
                    overview.tidligerePlaner shouldBe emptyList()
                    overview.aktivPlan shouldBe null
                }
            }

            it("returns status AKTIV_PLAN when a plan exists alongside unntaksvurderinger") {
                withTestApplication {
                    texasClientMock.defaultMocks(clientId = environment.syfoOppfolgingsplanFrontendClientId)
                    dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

                    testDb.persistUnntaksvurdering(pidInnlogetBruker, sykmeldt, "Maren Hegna")
                    testDb.persistOppfolgingsplan(
                        defaultPersistedOppfolgingsplan().copy(narmesteLederId = narmestelederId),
                    )

                    val response = client.get {
                        url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/oversikt")
                        bearerAuth("Bearer token")
                    }

                    val overview = response.body<ArbeidsgiverOppfolgingsplanOverviewResponse>().oversikt
                    overview.gjeldendeStatus shouldBe GjeldendeStatus.AKTIV_PLAN
                    overview.unntaksvurderinger.size shouldBe 1
                    overview.tidligerePlaner shouldBe emptyList()
                }
            }

            it("returns status UTKAST when only utkast exists alongside unntaksvurderinger") {
                withTestApplication {
                    texasClientMock.defaultMocks(clientId = environment.syfoOppfolgingsplanFrontendClientId)
                    dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

                    testDb.persistUnntaksvurdering(pidInnlogetBruker, sykmeldt, "Maren Hegna")
                    testDb.upsertOppfolgingsplanUtkast(
                        narmesteLederFnr = pidInnlogetBruker,
                        sykmeldt = sykmeldt,
                        lagreUtkastRequest = defaultUtkastRequest(),
                    )

                    val response = client.get {
                        url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/oversikt")
                        bearerAuth("Bearer token")
                    }

                    response.body<ArbeidsgiverOppfolgingsplanOverviewResponse>()
                        .oversikt.gjeldendeStatus shouldBe GjeldendeStatus.UTKAST
                }
            }

            it("backfills missing narmesteLederFullName from PDL and persists it") {
                withTestApplication {
                    texasClientMock.defaultMocks(clientId = environment.syfoOppfolgingsplanFrontendClientId)
                    dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)
                    coEvery { pdlServiceMock.getNameFor(pidInnlogetBruker) } returns "Backfilled Navn"

                    testDb.persistUnntaksvurdering(pidInnlogetBruker, sykmeldt, null)

                    val response = client.get {
                        url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/oversikt")
                        bearerAuth("Bearer token")
                    }

                    response.body<ArbeidsgiverOppfolgingsplanOverviewResponse>()
                        .oversikt.unntaksvurderinger.first().meldtAv.navn shouldBe "Backfilled Navn"
                    testDb.findAllUnntaksvurderingerBy(sykmeldt.fnr, sykmeldt.orgnummer)
                        .first().narmesteLederFullName shouldBe "Backfilled Navn"
                }
            }

            it("keeps navn null when PDL has no name") {
                withTestApplication {
                    texasClientMock.defaultMocks(clientId = environment.syfoOppfolgingsplanFrontendClientId)
                    dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)
                    coEvery { pdlServiceMock.getNameFor(pidInnlogetBruker) } returns null

                    testDb.persistUnntaksvurdering(pidInnlogetBruker, sykmeldt, null)

                    val response = client.get {
                        url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/oversikt")
                        bearerAuth("Bearer token")
                    }

                    response.body<ArbeidsgiverOppfolgingsplanOverviewResponse>()
                        .oversikt.unntaksvurderinger.first().meldtAv.navn shouldBe null
                }
            }

            it("filters out skjulte unntaksvurderinger") {
                withTestApplication {
                    texasClientMock.defaultMocks(clientId = environment.syfoOppfolgingsplanFrontendClientId)
                    dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

                    val uuid = testDb.persistUnntaksvurdering(pidInnlogetBruker, sykmeldt, "Maren Hegna")
                    testDb.connection.use { connection ->
                        connection.prepareStatement("UPDATE unntaksvurdering SET skjult_fra = NOW() WHERE uuid = ?").use {
                            it.setObject(1, uuid)
                            it.executeUpdate()
                        }
                        connection.commit()
                    }

                    val response = client.get {
                        url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/oversikt")
                        bearerAuth("Bearer token")
                    }

                    val overview = response.body<ArbeidsgiverOppfolgingsplanOverviewResponse>().oversikt
                    overview.unntaksvurderinger shouldBe emptyList()
                    overview.gjeldendeStatus shouldBe GjeldendeStatus.INGEN
                }
            }
        }

        describe("POST /unntaksvurderinger") {
            it("responds with Unauthorized when no token is provided") {
                withTestApplication {
                    val response = client.post("/api/v1/arbeidsgiver/$narmestelederId/unntaksvurderinger")
                    response.status shouldBe HttpStatusCode.Unauthorized
                }
            }

            it("responds with Forbidden when client is not allowed") {
                withTestApplication {
                    texasClientMock.defaultMocks(clientId = "cluster:another-namespace:another-app")

                    val response = client.post {
                        url("/api/v1/arbeidsgiver/$narmestelederId/unntaksvurderinger")
                        bearerAuth("Bearer token")
                    }

                    response.status shouldBe HttpStatusCode.Forbidden
                }
            }

            it("responds with Created and persists unntaksvurdering with name from PDL") {
                withTestApplication {
                    texasClientMock.defaultMocks(pidInnlogetBruker, clientId = environment.syfoOppfolgingsplanFrontendClientId)
                    dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)
                    coEvery { pdlServiceMock.getNameFor(pidInnlogetBruker) } returns "Maren Hegna"

                    val response = client.post {
                        url("/api/v1/arbeidsgiver/$narmestelederId/unntaksvurderinger")
                        bearerAuth("Bearer token")
                    }

                    response.status shouldBe HttpStatusCode.Created
                    val persisted = testDb.findAllUnntaksvurderingerBy(sykmeldt.fnr, sykmeldt.orgnummer)
                    persisted.size shouldBe 1
                    persisted.first().narmesteLederFnr shouldBe pidInnlogetBruker
                    persisted.first().narmesteLederFullName shouldBe "Maren Hegna"
                }
            }

            it("persists unntaksvurdering with null name when PDL has no name") {
                withTestApplication {
                    texasClientMock.defaultMocks(pidInnlogetBruker, clientId = environment.syfoOppfolgingsplanFrontendClientId)
                    dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)
                    coEvery { pdlServiceMock.getNameFor(pidInnlogetBruker) } returns null

                    val response = client.post {
                        url("/api/v1/arbeidsgiver/$narmestelederId/unntaksvurderinger")
                        bearerAuth("Bearer token")
                    }

                    response.status shouldBe HttpStatusCode.Created
                    testDb.findAllUnntaksvurderingerBy(sykmeldt.fnr, sykmeldt.orgnummer)
                        .first().narmesteLederFullName shouldBe null
                }
            }

            it("responds with Forbidden when sykmeldt has no active sykmelding") {
                withTestApplication {
                    texasClientMock.defaultMocks(pidInnlogetBruker, clientId = environment.syfoOppfolgingsplanFrontendClientId)
                    coEvery {
                        dineSykmeldteHttpClientMock.getSykmeldtForNarmesteLederId(narmestelederId, "token")
                    } returns defaultSykmeldt().copy(narmestelederId = narmestelederId, aktivSykmelding = false)

                    val response = client.post {
                        url("/api/v1/arbeidsgiver/$narmestelederId/unntaksvurderinger")
                        bearerAuth("Bearer token")
                    }

                    response.status shouldBe HttpStatusCode.Forbidden
                    testDb.findAllUnntaksvurderingerBy(sykmeldt.fnr, sykmeldt.orgnummer) shouldBe emptyList()
                }
            }

            it("responds with Conflict when an aktiv plan exists") {
                withTestApplication {
                    texasClientMock.defaultMocks(pidInnlogetBruker, clientId = environment.syfoOppfolgingsplanFrontendClientId)
                    dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

                    testDb.persistOppfolgingsplan(
                        defaultPersistedOppfolgingsplan().copy(narmesteLederId = narmestelederId),
                    )

                    val response = client.post {
                        url("/api/v1/arbeidsgiver/$narmestelederId/unntaksvurderinger")
                        bearerAuth("Bearer token")
                    }

                    response.status shouldBe HttpStatusCode.Conflict
                    testDb.findAllUnntaksvurderingerBy(sykmeldt.fnr, sykmeldt.orgnummer) shouldBe emptyList()
                }
            }

            it("responds with Created when only skjulte or feilregistrerte planer exist") {
                withTestApplication {
                    texasClientMock.defaultMocks(pidInnlogetBruker, clientId = environment.syfoOppfolgingsplanFrontendClientId)
                    dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)
                    coEvery { pdlServiceMock.getNameFor(pidInnlogetBruker) } returns "Maren Hegna"

                    testDb.persistOppfolgingsplan(
                        defaultPersistedOppfolgingsplan().copy(
                            narmesteLederId = narmestelederId,
                            skjultFra = Instant.now(),
                        ),
                    )
                    testDb.persistOppfolgingsplan(
                        defaultPersistedOppfolgingsplan().copy(
                            uuid = UUID.randomUUID(),
                            narmesteLederId = narmestelederId,
                            feilregistrert = Instant.now(),
                        ),
                    )

                    val response = client.post {
                        url("/api/v1/arbeidsgiver/$narmestelederId/unntaksvurderinger")
                        bearerAuth("Bearer token")
                    }

                    response.status shouldBe HttpStatusCode.Created
                    testDb.findAllUnntaksvurderingerBy(sykmeldt.fnr, sykmeldt.orgnummer).size shouldBe 1
                }
            }

            it("responds with Conflict when an utkast exists") {
                withTestApplication {
                    texasClientMock.defaultMocks(pidInnlogetBruker, clientId = environment.syfoOppfolgingsplanFrontendClientId)
                    dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)

                    testDb.upsertOppfolgingsplanUtkast(
                        narmesteLederFnr = pidInnlogetBruker,
                        sykmeldt = sykmeldt,
                        lagreUtkastRequest = defaultUtkastRequest(),
                    )

                    val response = client.post {
                        url("/api/v1/arbeidsgiver/$narmestelederId/unntaksvurderinger")
                        bearerAuth("Bearer token")
                    }

                    response.status shouldBe HttpStatusCode.Conflict
                    testDb.findAllUnntaksvurderingerBy(sykmeldt.fnr, sykmeldt.orgnummer) shouldBe emptyList()
                }
            }

            it("responds with Created when an unntaksvurdering already exists — newest decides status") {
                withTestApplication {
                    texasClientMock.defaultMocks(pidInnlogetBruker, clientId = environment.syfoOppfolgingsplanFrontendClientId)
                    dineSykmeldteHttpClientMock.defaultMocks(narmestelederId = narmestelederId)
                    coEvery { pdlServiceMock.getNameFor(pidInnlogetBruker) } returns "Maren Hegna"

                    val existing = testDb.persistUnntaksvurdering(pidInnlogetBruker, sykmeldt, "Maren Hegna")

                    val response = client.post {
                        url("/api/v1/arbeidsgiver/$narmestelederId/unntaksvurderinger")
                        bearerAuth("Bearer token")
                    }

                    response.status shouldBe HttpStatusCode.Created
                    val alle = testDb.findAllUnntaksvurderingerBy(sykmeldt.fnr, sykmeldt.orgnummer)
                    alle.size shouldBe 2
                    alle.last().uuid shouldBe existing

                    val overviewResponse = client.get {
                        url("/api/v1/arbeidsgiver/$narmestelederId/oppfolgingsplaner/oversikt")
                        bearerAuth("Bearer token")
                    }
                    val overview = overviewResponse.body<ArbeidsgiverOppfolgingsplanOverviewResponse>().oversikt
                    overview.gjeldendeStatus shouldBe GjeldendeStatus.IKKE_AKTUELT
                    overview.unntaksvurderinger.first().id shouldBe alle.first().uuid
                }
            }
        }
    })
