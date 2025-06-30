package no.nav.syfo.oppfolgingsplan.api.v1.arbeidsgiver

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.DescribeSpec
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.clearAllMocks
import io.mockk.mockk
import no.nav.syfo.TestDB
import no.nav.syfo.dinesykmeldte.DineSykmeldteHttpClient
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.oppfolgingsplan.api.v1.registerApiV1
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.plugins.installContentNegotiation
import no.nav.syfo.texas.client.TexasHttpClient

class OppfolgingsplanUtkastApiV1Test: DescribeSpec({

    val texasClientMock = mockk<TexasHttpClient>()
    val dineSykmeldteHttpClientMock = mockk<DineSykmeldteHttpClient>()
    val testDb = TestDB.Companion.database

    beforeTest {
        clearAllMocks()
        TestDB.Companion.clearAllData()
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
                        )
                    )
                }
            }
            fn(this)
        }
    }
    describe("Oppfolgingsplan Utkast API V1") {
        it("should handle PUT request to create or update an oppfolgingsplan utkast") {
            withTestApplication {
                // TODO
            }
        }

        it("should handle GET request to retrieve an oppfolgingsplan utkast") {
            withTestApplication {
                // TODO
            }
        }
    }
})