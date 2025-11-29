package no.nav.syfo.pdfgen

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import io.ktor.http.isSuccess
import io.mockk.clearAllMocks
import io.mockk.mockk
import no.nav.syfo.TestDB
import no.nav.syfo.defaultPersistedOppfolgingsplan
import no.nav.syfo.pdfgen.client.PdfGenClient
import no.nav.syfo.util.httpClientDefault
import org.junit.jupiter.api.assertThrows

class PdfGenServiceTest : DescribeSpec({

    fun getMockEngine(status: HttpStatusCode, headers: Headers, content: String) = MockEngine { request ->
        when (request.url.fullPath) {
            "/api/v1/genpdf/oppfolgingsplan/oppfolgingsplan_v1" -> {
                if (status.isSuccess()) {
                    respond(
                        status = status,
                        headers = headers,
                        content = content.toByteArray(Charsets.UTF_8),
                    )
                } else {
                    respond(
                        status = status,
                        headers = headers,
                        content = content,
                    )
                }
            }

            else -> error("Unhandled request ${request.url.fullPath}")
        }
    }

    beforeTest {
        clearAllMocks()
        TestDB.clearAllData()
    }

    describe("PdfGenService") {
        it("generatePdf and outgoing call with client succeeds") {
            val client = httpClientDefault(
                HttpClient(
                    getMockEngine(
                        HttpStatusCode.OK, headersOf(), content = "whatever"
                    )
                )
            )
            val persistedPlan = defaultPersistedOppfolgingsplan()
            val myService = PdfGenService(
                PdfGenClient(client, ""),
                mockk(relaxed = true) // Mock OppfolgingsplanService, as it's not the focus
            )
            val response = myService.generatePdf(persistedPlan)
            response shouldNotBe null
            response?.toString(Charsets.UTF_8) shouldBe "whatever"
        }

        it("generatePdf should throw RuntimeException when outgoing client call fails") {
            val client = httpClientDefault(
                HttpClient(
                    getMockEngine(
                        HttpStatusCode.BadRequest,
                        headersOf(HttpHeaders.ContentType, "text/plain"),
                        content = "Forced Error"
                    )
                )
            )
            val myService = PdfGenService(
                PdfGenClient(client, ""),
                mockk(relaxed = true) // Mock OppfolgingsplanService, as it's not the focus
            )
            val persistedPlan = defaultPersistedOppfolgingsplan()
            assertThrows<RuntimeException> {
                myService.generatePdf(persistedPlan)
            }
        }
    }
})
