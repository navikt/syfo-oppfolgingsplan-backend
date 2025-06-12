package no.nav.syfo.oppfolgingsplan

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.dinesykmeldte.DineSykmeldteHttpClient
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.texas.TexasHttpClient
import no.nav.syfo.texas.TexasIntrospectionResponse

class OppfolgingsplanApiTest : DescribeSpec({

    val texasClientMock = mockk<TexasHttpClient>()
    val dineSykmeldteClientMock = mockk<DineSykmeldteHttpClient>()

    describe("Oppfolgingsplan API") {
        it("GET /oppfolgingsplaner should respond with Unauthorized when no authentication is provided") {
            testApplication {
                // Arrange
                application {
                    routing {
                        registerOppfolgingsplanApi(
                            texasClientMock,
                            DineSykmeldteService(dineSykmeldteClientMock
                            )
                        )
                    }
                }
                // Act
                val response = client.get("/api/v1/oppfolgingsplaner")
                // Assert
                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }
        it("GET /oppfolgingsplaner should respond with Unauthorized when no bearer token is provided") {
            testApplication {
                // Arrange
                application {
                    routing {
                        registerOppfolgingsplanApi(
                            texasClientMock,
                            DineSykmeldteService(dineSykmeldteClientMock
                            )
                        )
                    }
                }
                // Act
                val response = client.get {
                    url("/api/v1/oppfolgingsplaner")
                    headers.append("Authorization", "")
                }
                // Assert
                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }
        it("GET /oppfolgingsplaner should respond with OK when texas client gives active response") {
            testApplication {
                // Arrange
                application {
                    routing {
                        registerOppfolgingsplanApi(
                            texasClientMock,
                            DineSykmeldteService(dineSykmeldteClientMock
                            )
                        )
                    }
                }
                coEvery {
                    texasClientMock.introspectToken(any(), any())
                } returns TexasIntrospectionResponse(active = true, pid = "userIdentifier", acr = "Level4")

                // Act
                val response = client.get {
                    url("/api/v1/oppfolgingsplaner")
                    headers.append("Authorization", "Bearer token")
                }
                // Assert
                response.status shouldBe HttpStatusCode.OK
            }
        }
        it("GET /oppfolgingsplaner should respond with Forbidden when texas acr claim is not Level4") {
            testApplication {
                // Arrange
                application {
                    routing {
                        registerOppfolgingsplanApi(
                            texasClientMock,
                            DineSykmeldteService(dineSykmeldteClientMock
                            )
                        )
                    }
                }
                coEvery {
                    texasClientMock.introspectToken(any(), any())
                } returns TexasIntrospectionResponse(active = true, pid = "userIdentifier", acr = "Level3")

                // Act
                val response = client.get {
                    url("/api/v1/oppfolgingsplaner")
                    headers.append("Authorization", "Bearer token")
                }
                // Assert
                response.status shouldBe HttpStatusCode.Forbidden
            }
        }
        it("GET /oppfolgingsplaner should respond with Unauthorized when texas client gives inactive response") {
            testApplication {
                // Arrange
                application {
                    routing {
                        registerOppfolgingsplanApi(
                            texasClientMock,
                            DineSykmeldteService(dineSykmeldteClientMock
                            )
                        )
                    }
                }
                coEvery {
                    texasClientMock.introspectToken(any(), any())
                } returns TexasIntrospectionResponse(active = false, sub = "user")

                // Act
                val response = client.get {
                    url("/api/v1/oppfolgingsplaner")
                    headers.append("Authorization", "Bearer token")
                }
                // Assert
                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }
    }
})