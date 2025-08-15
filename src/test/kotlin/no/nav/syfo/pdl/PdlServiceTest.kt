package no.nav.syfo.pdl

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.client.model.PersonResponse
import no.nav.syfo.pdl.client.model.ResponseData

class PdlServiceTest : DescribeSpec({

    val pdlClient = mockk<PdlClient>()
    val service = PdlService(pdlClient)

    beforeTest {
        clearAllMocks()
    }

    describe("PdlService.getNameFor") {
        it("returns full name when fornavn, mellomnavn and etternavn are present") {
            // Arrange
            coEvery { pdlClient.getPerson("12345678910") } returns GetPersonResponse(
                data = ResponseData(
                    person = PersonResponse(
                        navn = listOf(
                            no.nav.syfo.pdl.client.model.Navn(
                                fornavn = "Kari",
                                mellomnavn = "Nordmann",
                                etternavn = "Olsen"
                            )
                        )
                    ),
                    identer = null
                ),
                errors = null
            )

            // Act
            val result = service.getNameFor("12345678910")

            // Assert
            result shouldBe "Kari Nordmann Olsen"
        }

        it("returns name without middle name when mellomnavn is null") {
            // Arrange
            coEvery { pdlClient.getPerson("10987654321") } returns GetPersonResponse(
                data = ResponseData(
                    person = PersonResponse(
                        navn = listOf(
                            no.nav.syfo.pdl.client.model.Navn(
                                fornavn = "Ola",
                                mellomnavn = null,
                                etternavn = "Nordmann"
                            )
                        )
                    ),
                    identer = null
                ),
                errors = null
            )

            // Act
            val result = service.getNameFor("10987654321")

            // Assert
            result shouldBe "Ola Nordmann"
        }

        it("returns null when PDL returns no person") {
            // Arrange
            coEvery { pdlClient.getPerson(any()) } returns GetPersonResponse(
                data = ResponseData(
                    person = null,
                    identer = null
                ),
                errors = null
            )

            // Act
            val result = service.getNameFor("00000000000")

            // Assert
            result shouldBe null
        }

        it("returns null when navn list is empty") {
            // Arrange
            coEvery { pdlClient.getPerson(any()) } returns GetPersonResponse(
                data = ResponseData(
                    person = PersonResponse(navn = emptyList()),
                    identer = null
                ),
                errors = null
            )

            // Act
            val result = service.getNameFor("11111111111")

            // Assert
            result shouldBe null
        }

        it("returns null when PdlClient throws") {
            // Arrange
            coEvery { pdlClient.getPerson(any()) } throws RuntimeException("boom")

            // Act
            val result = service.getNameFor("22222222222")

            // Assert
            result shouldBe null
        }
    }
})
