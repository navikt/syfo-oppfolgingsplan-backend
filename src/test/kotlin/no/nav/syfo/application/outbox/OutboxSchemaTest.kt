package no.nav.syfo.application.outbox

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import no.nav.syfo.TestDB
import no.nav.syfo.application.database.exposedTransaction
import no.nav.syfo.application.outbox.db.findOutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxStatus

class OutboxSchemaTest :
    DescribeSpec({
        beforeTest { TestDB.clearAllData() }

        describe("generic outbox migration") {
            it("creates the domain-neutral table and ready-message index") {
                val columns = TestDB.database.connection.use { connection ->
                    connection.metaData.getColumns(null, null, "outbox", null).use { resultSet ->
                        buildMap {
                            while (resultSet.next()) {
                                put(
                                    resultSet.getString("COLUMN_NAME"),
                                    resultSet.getString("IS_NULLABLE") == "YES",
                                )
                            }
                        }
                    }
                }
                val indexes = TestDB.database.connection.use { connection ->
                    connection.metaData.getIndexInfo(null, null, "outbox", false, false).use { resultSet ->
                        buildSet {
                            while (resultSet.next()) {
                                resultSet.getString("INDEX_NAME")?.let(::add)
                            }
                        }
                    }
                }

                columns.keys shouldContainAll setOf(
                    "uuid",
                    "message_type",
                    "dedup_key",
                    "external_ref",
                    "payload",
                    "available_at",
                    "status",
                    "claim_token",
                    "lease_until",
                    "failure_count",
                    "last_failure_at",
                    "created_at",
                    "sent_at",
                    "cancellation_reason",
                )
                columns["uuid"] shouldBe false
                columns["payload"] shouldBe false
                columns["sent_at"] shouldBe true
                columns["cancellation_reason"] shouldBe true
                columns["claim_token"] shouldBe true
                columns["lease_until"] shouldBe true
                indexes shouldContainAll setOf("uq_outbox_message", "idx_outbox_ready", "idx_outbox_expired_claim")
            }

            it("rejects state metadata combinations that the domain cannot represent") {
                val message = TestDB.database.enqueueTestOutboxMessage()

                shouldThrow<Exception> {
                    TestDB.database.exposedTransaction {
                        exec("UPDATE outbox SET status = 'SENT' WHERE uuid = '${message.uuid}'")
                    }
                }

                TestDB.database.findOutboxMessage(message)?.status shouldBe OutboxStatus.READY
            }
        }
    })
