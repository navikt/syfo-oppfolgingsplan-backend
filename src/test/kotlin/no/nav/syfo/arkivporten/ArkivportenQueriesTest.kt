package no.nav.syfo.arkivporten

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import java.time.Instant
import no.nav.syfo.TestDB
import no.nav.syfo.arkivporten.client.FakeArkivportenClient
import no.nav.syfo.defaultPersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanserForArkivportenPublisering
import no.nav.syfo.oppfolgingsplan.db.setPublisertTilArkivportenTidspunkt
import no.nav.syfo.pdfgen.PdfGenService
import no.nav.syfo.persistOppfolgingsplan

class ArkivportenQueriesTest : DescribeSpec({
    val client = FakeArkivportenClient()
    val testDb = TestDB.database

    beforeTest {
        clearAllMocks()
        TestDB.clearAllData()
    }

    describe("Database queries") {
        it("findAndPublishOppfolgingsplaner shoud return planer where sendt_til_arkivporten_tidspunkt is null") {
            val first = testDb.persistOppfolgingsplan(defaultPersistedOppfolgingsplan())
            val second = testDb.persistOppfolgingsplan(defaultPersistedOppfolgingsplan())
            val third = testDb.persistOppfolgingsplan(defaultPersistedOppfolgingsplan())
            testDb.setPublisertTilArkivportenTidspunkt(third, Instant.now())
            // Act
            val unsendtPlans = testDb.findOppfolgingsplanserForArkivportenPublisering()
            unsendtPlans.size shouldBe 2
            unsendtPlans.find { it.uuid == first } shouldNotBe null
            unsendtPlans.find { it.uuid == second } shouldNotBe null
        }

        it("setPublisertTilArkivportenTidspunkt should set property in field send_arkivporten_tidspunkt is null") {
            val first = testDb.persistOppfolgingsplan(defaultPersistedOppfolgingsplan())
            val second = testDb.persistOppfolgingsplan(defaultPersistedOppfolgingsplan())
            val third = testDb.persistOppfolgingsplan(defaultPersistedOppfolgingsplan())
            testDb.setPublisertTilArkivportenTidspunkt(third, Instant.now())
            // Act
            val unsendtPlans = testDb.findOppfolgingsplanserForArkivportenPublisering()
            unsendtPlans.size shouldBe 2
            unsendtPlans.find { it.uuid == first } shouldNotBe null
            unsendtPlans.find { it.uuid == second } shouldNotBe null
        }
    }
})
