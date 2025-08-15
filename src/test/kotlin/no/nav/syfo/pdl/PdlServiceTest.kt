package no.nav.syfo.pdl

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.client.model.Navn
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
                            Navn(
                                fornavn = "For",
                                mellomnavn = "Mellom",
                                etternavn = "Etter"
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
            result shouldBe "For Mellom Etter"
        }

        it("returns name without middle name when mellomnavn is null") {
            // Arrange
            coEvery { pdlClient.getPerson("10987654321") } returns GetPersonResponse(
                data = ResponseData(
                    person = PersonResponse(
                        navn = listOf(
                            Navn(
                                fornavn = "For",
                                mellomnavn = null,
                                etternavn = "Etter"
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
            result shouldBe "For Etter"
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
