package no.nav.syfo.application.outbox

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.syfo.TestDB
import no.nav.syfo.application.outbox.db.claimNextReadyOutbox
import no.nav.syfo.application.outbox.db.findOutboxFor
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import no.nav.syfo.application.outbox.domain.OutboxStatus
import java.sql.Connection
import java.time.Instant

class OutboxDAOTest :
    DescribeSpec({
        val database = TestDB.database

        beforeTest { TestDB.clearAllData() }

        it("claims the oldest ready row") {
            val oldest = database.addOutboxMessage(dedupKey = "oldest")
            database.addOutboxMessage(dedupKey = "newest")

            database.execute {
                it.claimNextReadyOutbox(OutboxMessageType.PAAMINNELSE_OPPFOLGINGSPLAN, Instant.now())
            }.shouldNotBeNull().uuid shouldBe oldest.uuid
        }

        it("returns no row when the queue is empty") {
            database.execute {
                it.claimNextReadyOutbox(OutboxMessageType.PAAMINNELSE_OPPFOLGINGSPLAN, Instant.now())
            }.shouldBeNull()
        }

        it("uses deduplication without reviving terminal rows") {
            val message = database.addOutboxMessage(dedupKey = "dedup")
            database.setOutboxStatus(message.uuid, OutboxStatus.SENDT)

            database.addOutboxMessage(dedupKey = "dedup")

            database.getOutboxMessage(message.uuid).shouldNotBeNull().status shouldBe OutboxStatus.SENDT
            database.execute {
                it.findOutboxFor(OutboxMessageType.PAAMINNELSE_OPPFOLGINGSPLAN, "dedup")
            }.shouldNotBeNull().uuid shouldBe message.uuid
        }

        it("reactivates an irrelevant row when the reminder is ordered again") {
            val message = database.addOutboxMessage(dedupKey = "reactivate")
            database.setOutboxStatus(message.uuid, OutboxStatus.IKKE_RELEVANT)

            database.addOutboxMessage(dedupKey = "reactivate")

            database.getOutboxMessage(message.uuid).shouldNotBeNull().status shouldBe OutboxStatus.KLAR
        }

        it("gives concurrent transactions separate rows") {
            repeat(3) { database.addOutboxMessage(dedupKey = "concurrent-$it") }
            val connections = List(2) { TestDB.newConnection() }
            try {
                connections.forEach { it.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED }
                val claimed = connections.map {
                    it.claimNextReadyOutbox(OutboxMessageType.PAAMINNELSE_OPPFOLGINGSPLAN, Instant.now())
                        .shouldNotBeNull()
                        .uuid
                }
                claimed.toSet().size shouldBe 2
            } finally {
                connections.forEach {
                    it.rollback()
                    it.close()
                }
            }
        }
    })
