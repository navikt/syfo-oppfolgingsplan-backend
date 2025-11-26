package no.nav.syfo.arkivporten

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearAllMocks
import no.nav.syfo.TestDB
import no.nav.syfo.arkivporten.client.FakeArkivportenClient
import no.nav.syfo.defaultPersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanerForArkivportenPublisering
import no.nav.syfo.oppfolgingsplan.db.setSendtTilArkivportenTidspunkt
import no.nav.syfo.persistOppfolgingsplan
import java.time.Instant

class ArkivportenQueriesTest : DescribeSpec({
    FakeArkivportenClient()
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
            testDb.setSendtTilArkivportenTidspunkt(third, Instant.now())
            // Act
            val unsendtPlans = testDb.findOppfolgingsplanerForArkivportenPublisering()
            unsendtPlans.size shouldBe 2
            unsendtPlans.find { it.uuid == first } shouldNotBe null
            unsendtPlans.find { it.uuid == second } shouldNotBe null
        }

        it("setPublisertTilArkivportenTidspunkt should set property in field send_arkivporten_tidspunkt is null") {
            val first = testDb.persistOppfolgingsplan(defaultPersistedOppfolgingsplan())
            val second = testDb.persistOppfolgingsplan(defaultPersistedOppfolgingsplan())
            val third = testDb.persistOppfolgingsplan(defaultPersistedOppfolgingsplan())
            testDb.setSendtTilArkivportenTidspunkt(third, Instant.now())
            // Act
            val unsendtPlans = testDb.findOppfolgingsplanerForArkivportenPublisering()
            unsendtPlans.size shouldBe 2
            unsendtPlans.find { it.uuid == first } shouldNotBe null
            unsendtPlans.find { it.uuid == second } shouldNotBe null
        }
    }
})
