package no.nav.syfo.oppfolgingsplan.api.v1.veileder

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.mockk.coEvery
import io.mockk.coVerify
import no.nav.syfo.defaultMocks
import no.nav.syfo.defaultSykmeldt
import no.nav.syfo.oppfolgingsplan.db.persistUnntaksvurdering
import no.nav.syfo.oppfolgingsplan.domain.Fodselsnummer

class VeilederUnntaksvurderingApiV1Test :
    DescribeSpec({
        val fixture = VeilederApiV1TestFixture()
        val texasClientMock = fixture.texasClientMock
        val testDb = fixture.testDb
        val sykmeldtFnr = fixture.sykmeldtFnr
        val isTilgangskontrollClientMock = fixture.isTilgangskontrollClientMock
        val syfomodiapersonClientId = fixture.syfomodiapersonClientId

        beforeTest {
            fixture.reset()
        }

        it("responds with Unauthorized when no authentication is provided") {
            fixture.withTestApplication {
                val response = client.post("/api/v1/veileder/unntaksvurderinger/query")

                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        it("returns all visible unntaksvurderinger newest first") {
            fixture.withTestApplication {
                texasClientMock.defaultMocks(
                    pid = "some-veileder-token",
                    navident = "some-navident",
                    azpName = syfomodiapersonClientId,
                )
                coEvery { isTilgangskontrollClientMock.harTilgangTilSykmeldt(any(), any()) } returns true
                val sykmeldt = defaultSykmeldt().copy(fnr = sykmeldtFnr)
                val first = testDb.persistUnntaksvurdering("10987654321", sykmeldt, "Maren Hegna")
                val second = testDb.persistUnntaksvurdering(
                    "10987654321",
                    sykmeldt.copy(orgnummer = "annetorgnummer"),
                    "Maren Hegna",
                )
                testDb.persistUnntaksvurdering(
                    "10987654321",
                    sykmeldt.copy(fnr = "99999999999"),
                    "Maren Hegna",
                )

                val response = client.post {
                    url("/api/v1/veileder/unntaksvurderinger/query")
                    bearerAuth(token = "******")
                    contentType(ContentType.Application.Json)
                    setBody(SykmeldtReadRequest(sykmeldtFnr))
                }

                response.status shouldBe HttpStatusCode.OK
                val responseBody = response.body<UnntaksvurderingerVeilederResponse>()
                responseBody.unntaksvurderinger.map { it.uuid } shouldBe listOf(second, first)
                responseBody.unntaksvurderinger.first().fnr shouldBe sykmeldtFnr
                responseBody.unntaksvurderinger.first().organisasjonsnummer shouldBe "annetorgnummer"
                responseBody.unntaksvurderinger.first().organisasjonsnavn shouldBe "Test AS"
                coVerify(exactly = 1) {
                    isTilgangskontrollClientMock.harTilgangTilSykmeldt(
                        sykmeldtFnr = eq(Fodselsnummer(sykmeldtFnr)),
                        token = any(),
                    )
                }
            }
        }

        it("responds with Forbidden when tilgangskontroll rejects access") {
            fixture.withTestApplication {
                texasClientMock.defaultMocks(
                    pid = "some-veileder-token",
                    navident = "some-navident",
                    azpName = syfomodiapersonClientId,
                )
                coEvery { isTilgangskontrollClientMock.harTilgangTilSykmeldt(any(), any()) } returns false

                val response = client.post {
                    url("/api/v1/veileder/unntaksvurderinger/query")
                    bearerAuth(token = "******")
                    contentType(ContentType.Application.Json)
                    setBody(SykmeldtReadRequest(sykmeldtFnr))
                }

                response.status shouldBe HttpStatusCode.Forbidden
            }
        }
    })
