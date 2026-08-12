package no.nav.syfo.application.outbox

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import no.nav.syfo.TestDB

class OutboxSchemaTest :
    DescribeSpec({
        describe("outbox migration") {
            it("creates the outbox table and ready-message index") {
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
                    "scheduled_at",
                    "status",
                    "attempt_count",
                    "last_attempt_at",
                    "created_at",
                    "sent_at",
                )
                columns["uuid"] shouldBe false
                columns["message_type"] shouldBe false
                columns["sent_at"] shouldBe true
                indexes shouldContainAll setOf("uq_outbox_message", "idx_outbox_ready")
            }

            it("assigns event IDs to plans inserted by old application instances") {
                val eventIdDefault = TestDB.database.connection.use { connection ->
                    connection.metaData.getColumns(null, null, "oppfolgingsplan", "event_id").use { resultSet ->
                        resultSet.next() shouldBe true
                        resultSet.getString("COLUMN_DEF")
                    }
                }

                eventIdDefault shouldBe "gen_random_uuid()"
            }
        }
    })
