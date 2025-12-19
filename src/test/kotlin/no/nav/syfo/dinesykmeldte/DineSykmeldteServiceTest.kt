package no.nav.syfo.dinesykmeldte

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.application.valkey.ValkeyCache
import no.nav.syfo.dinesykmeldte.client.DineSykmeldteSykmelding
import no.nav.syfo.dinesykmeldte.client.IDineSykmeldteHttpClient
import no.nav.syfo.dinesykmeldte.client.Sykmeldt

class DineSykmeldteServiceTest : DescribeSpec({
    val dineSykmeldteHttpClient = mockk<IDineSykmeldteHttpClient>()
    val valkeyCache = mockk<ValkeyCache>()
    val service = DineSykmeldteService(dineSykmeldteHttpClient, valkeyCache)

    val narmestelederId = "nl-id"
    val lederFnr = "12345678901"
    val token = "token"

    beforeTest { clearAllMocks() }

    describe("getSykmeldtForNarmesteleder") {
        it("returns cached sykmeldt and does not call http client on cache hit") {
            // Arrange
            val cached = Sykmeldt(
                narmestelederId = narmestelederId,
                orgnummer = "999888777",
                fnr = "10987654321",
                navn = "Kari Testperson",
                sykmeldinger = listOf(DineSykmeldteSykmelding(arbeidsgiver = "Arbeidsgiver AS")),
                aktivSykmelding = true
            )
            every { valkeyCache.getSykmeldt(lederFnr, narmestelederId) } returns cached

            // Act
            val result = service.getSykmeldtForNarmesteleder(narmestelederId, lederFnr, token)

            // Assert
            result shouldBe cached
            coVerify(exactly = 0) { dineSykmeldteHttpClient.getSykmeldtForNarmesteLederId(any(), any()) }
            verify(exactly = 0) { valkeyCache.putSykmeldt(any(), any(), any()) }
        }

        it("calls http client and caches result on cache miss") {
            // Arrange
            every { valkeyCache.getSykmeldt(lederFnr, narmestelederId) } returns null
            val fetched = Sykmeldt(
                narmestelederId = narmestelederId,
                orgnummer = "123456789",
                fnr = "01987654321",
                navn = "Ola Testperson",
                sykmeldinger = listOf(DineSykmeldteSykmelding(arbeidsgiver = "Firma AS")),
                aktivSykmelding = true
            )
            coEvery { dineSykmeldteHttpClient.getSykmeldtForNarmesteLederId(narmestelederId, token) } returns fetched
            every { valkeyCache.putSykmeldt(lederFnr, narmestelederId, fetched) } returns Unit

            // Act
            val result = service.getSykmeldtForNarmesteleder(narmestelederId, lederFnr, token)

            // Assert
            result shouldBe fetched
            coVerify(exactly = 1) { dineSykmeldteHttpClient.getSykmeldtForNarmesteLederId(narmestelederId, token) }
            verify(exactly = 1) { valkeyCache.putSykmeldt(lederFnr, narmestelederId, fetched) }
        }

        it("calls http client and does not cache result when aktivSykmelding = false") {
            // Arrange
            every { valkeyCache.getSykmeldt(lederFnr, narmestelederId) } returns null
            val fetched = Sykmeldt(
                narmestelederId = narmestelederId,
                orgnummer = "123456789",
                fnr = "01987654321",
                navn = "Ola Testperson",
                sykmeldinger = listOf(DineSykmeldteSykmelding(arbeidsgiver = "Firma AS")),
                aktivSykmelding = false
            )
            coEvery { dineSykmeldteHttpClient.getSykmeldtForNarmesteLederId(narmestelederId, token) } returns fetched
            every { valkeyCache.putSykmeldt(lederFnr, narmestelederId, fetched) } returns Unit

            // Act
            val result = service.getSykmeldtForNarmesteleder(narmestelederId, lederFnr, token)

            // Assert
            result shouldBe fetched
            coVerify(exactly = 1) { dineSykmeldteHttpClient.getSykmeldtForNarmesteLederId(narmestelederId, token) }
            verify(exactly = 0) { valkeyCache.putSykmeldt(lederFnr, narmestelederId, fetched) }
        }
    }
})
