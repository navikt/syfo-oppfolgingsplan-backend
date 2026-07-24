package no.nav.syfo.oppfolgingsplan.db

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.syfo.TestDB
import no.nav.syfo.defaultPersistedOppfolgingsplan
import no.nav.syfo.findEventIdForOppfolgingsplan
import no.nav.syfo.persistOppfolgingsplan
import java.util.UUID

class OppfolgingsplanEventIdDAOTest :
    DescribeSpec({
        beforeTest {
            TestDB.clearAllData()
        }

        afterTest {
            TestDB.clearAllData()
        }

        describe("generateEventIdTransactionally") {
            it("commits generated event id when block succeeds") {
                val oppfolgingsplanUuid = TestDB.database.persistOppfolgingsplan(defaultPersistedOppfolgingsplan())
                var generatedEventId: UUID? = null

                TestDB.database.generateEventIdTransactionally(oppfolgingsplanUuid) { eventId ->
                    generatedEventId = eventId
                }

                generatedEventId.shouldNotBeNull()
                TestDB.database.findEventIdForOppfolgingsplan(oppfolgingsplanUuid) shouldBe generatedEventId
            }

            it("rolls back generated event id when block throws") {
                val oppfolgingsplanUuid = TestDB.database.persistOppfolgingsplan(defaultPersistedOppfolgingsplan())

                val exception = shouldThrow<IllegalArgumentException> {
                    TestDB.database.generateEventIdTransactionally(oppfolgingsplanUuid) {
                        throw IllegalArgumentException("boom")
                    }
                }

                exception.message shouldBe "boom"
                TestDB.database.findEventIdForOppfolgingsplan(oppfolgingsplanUuid).shouldBeNull()
            }

            it("throws when oppfolgingsplan is missing without invoking block and leaves connection usable") {
                val persistedOppfolgingsplanUuid = TestDB.database.persistOppfolgingsplan(defaultPersistedOppfolgingsplan())
                var blockInvoked = false

                val exception = shouldThrow<IllegalStateException> {
                    TestDB.database.generateEventIdTransactionally(UUID.randomUUID()) {
                        blockInvoked = true
                    }
                }

                exception.message shouldBe "Oppfolgingsplan not found"
                blockInvoked.shouldBeFalse()

                var generatedEventId: UUID? = null
                TestDB.database.generateEventIdTransactionally(persistedOppfolgingsplanUuid) { eventId ->
                    generatedEventId = eventId
                }

                generatedEventId.shouldNotBeNull()
                TestDB.database.findEventIdForOppfolgingsplan(persistedOppfolgingsplanUuid) shouldBe generatedEventId
            }

            it("allows a later successful transaction after rollback on another oppfolgingsplan") {
                val failingOppfolgingsplanUuid = TestDB.database.persistOppfolgingsplan(defaultPersistedOppfolgingsplan())
                val succeedingOppfolgingsplanUuid = TestDB.database.persistOppfolgingsplan(
                    defaultPersistedOppfolgingsplan().copy(
                        sykmeldtFnr = "10987654321",
                        narmesteLederId = UUID.randomUUID().toString(),
                    ),
                )

                shouldThrow<RuntimeException> {
                    TestDB.database.generateEventIdTransactionally(failingOppfolgingsplanUuid) {
                        throw RuntimeException("rollback")
                    }
                }

                var generatedEventId: UUID? = null
                TestDB.database.generateEventIdTransactionally(succeedingOppfolgingsplanUuid) { eventId ->
                    generatedEventId = eventId
                }

                TestDB.database.findEventIdForOppfolgingsplan(failingOppfolgingsplanUuid).shouldBeNull()
                generatedEventId.shouldNotBeNull()
                TestDB.database.findEventIdForOppfolgingsplan(succeedingOppfolgingsplanUuid) shouldBe generatedEventId
            }
        }
    })
