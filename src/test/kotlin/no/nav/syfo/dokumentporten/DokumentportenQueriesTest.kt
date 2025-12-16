package no.nav.syfo.dokumentporten

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearAllMocks
import java.time.Instant
import no.nav.syfo.TestDB
import no.nav.syfo.defaultPersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanserForDokumentportenPublisering
import no.nav.syfo.oppfolgingsplan.db.setSendtTilDokumentportenTidspunkt
import no.nav.syfo.persistOppfolgingsplan

class DokumentportenQueriesTest : DescribeSpec({
    val testDb = TestDB.database

    beforeTest {
        clearAllMocks()
        TestDB.clearAllData()
    }

    describe("Database queries") {
        it("findOppfolgingsplanserForDokumentportenPublisering should return planer where sendt_til_dokumentporten_tidspunkt is null") {
            val first = testDb.persistOppfolgingsplan(defaultPersistedOppfolgingsplan())
            val second = testDb.persistOppfolgingsplan(defaultPersistedOppfolgingsplan())
            val third = testDb.persistOppfolgingsplan(defaultPersistedOppfolgingsplan())
            testDb.setSendtTilDokumentportenTidspunkt(third, Instant.now())
            // Act
            val unsendtPlans = testDb.findOppfolgingsplanserForDokumentportenPublisering()
            unsendtPlans.size shouldBe 2
            unsendtPlans.find { it.uuid == first } shouldNotBe null
            unsendtPlans.find { it.uuid == second } shouldNotBe null
        }
    }
})
