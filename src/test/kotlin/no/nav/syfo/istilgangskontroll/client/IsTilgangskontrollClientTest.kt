package no.nav.syfo.istilgangskontroll.client

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.isSuccess
import no.nav.syfo.dokarkiv.client.DokarkivClient
import no.nav.syfo.oppfolgingsplan.domain.Fodselsnummer
import no.nav.syfo.util.httpClientDefault

class IsTilgangskontrollClientTest : DescribeSpec({
    fun getMockEngine(status: HttpStatusCode, headers: Headers, content: String) = MockEngine.Companion { request ->
        when (request.url.fullPath) {
            "/api/tilgang/navident/person" -> {
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
    describe("IsTilgangskontrollClient")
    {
        it("should return true when isTilgangkontroll responseds with erGodkjent") {
            val mockEngine = getMockEngine(
                status = HttpStatusCode.OK,
                headers = Headers.build {
                    append("Content-Type", "application/json")
                },
                content = """{"erGodkjent": true}""",
            )
            val client = IsTilgangskontrollClient(httpClientDefault(HttpClient(mockEngine)), "")

            val result = client.harTilgangTilSykmeldt(
                Fodselsnummer("12345678901"),
                token = "test-token",
            )

            result shouldBe true
        }

        it("should return false when isTilgangkontroll responseds with erGodkjent") {
            val mockEngine = getMockEngine(
                status = HttpStatusCode.OK,
                headers = Headers.build {
                    append("Content-Type", "application/json")
                },
                content = """{"erGodkjent": false}""",
            )
            val client = IsTilgangskontrollClient(httpClientDefault(HttpClient(mockEngine)), "")

            val result = client.harTilgangTilSykmeldt(
                Fodselsnummer("12345678901"),
                token = "test-token",
            )

            result shouldBe false
        }

        it("should return false when isTilgangkontroll responseds with forbidden") {
            val mockEngine = getMockEngine(
                status = HttpStatusCode.Forbidden,
                headers = Headers.build {
                    append("Content-Type", "application/json")
                },
                content = "{}",
            )
            val client = IsTilgangskontrollClient(httpClientDefault(HttpClient(mockEngine)), "")

            val result = client.harTilgangTilSykmeldt(
                Fodselsnummer("12345678901"),
                token = "test-token",
            )

            result shouldBe false
        }

        it("should return false when isTilgangkontroll responds with service unavailable") {
            val mockEngine = getMockEngine(
                status = HttpStatusCode.ServiceUnavailable,
                headers = Headers.build {
                    append("Content-Type", "application/json")
                },
                content = "{}",
            )
            val client = IsTilgangskontrollClient(httpClientDefault(HttpClient(mockEngine)), "")

            val result = client.harTilgangTilSykmeldt(
                Fodselsnummer("12345678901"),
                token = "test-token",
            )

            result shouldBe false
        }
    }
})
