package no.nav.syfo.application.outbox

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.annotation.Isolate
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import no.nav.syfo.TestDB
import no.nav.syfo.application.database.exposedTransaction
import no.nav.syfo.application.outbox.db.OutboxTable
import no.nav.syfo.application.outbox.db.findOutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxStatus
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils

@Isolate
class OutboxSchemaTest :
    DescribeSpec({
        beforeTest { TestDB.clearAllData() }

        describe("generic outbox migration") {
            it("keeps the Exposed mapping aligned with the Flyway schema") {
                val drift = transaction(TestDB.database.exposedDatabase) {
                    MigrationUtils.statementsRequiredForDatabaseMigration(OutboxTable, withLogs = false)
                }

                withClue("Outbox schema drift:\n${drift.joinToString("\n")}") {
                    drift.shouldBeEmpty()
                }
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

            it("creates the reminder supersede index in a valid state") {
                val indexState = TestDB.database.connection.use { connection ->
                    connection.prepareStatement(
                        """
                        SELECT indexrelid::regclass::text AS index_name, indisvalid
                        FROM pg_index
                        WHERE indexrelid = 'idx_outbox_ready_message_type_external_ref'::regclass
                        """.trimIndent(),
                    ).use { statement ->
                        statement.executeQuery().use { resultSet ->
                            resultSet.next() shouldBe true
                            resultSet.getString("index_name") to resultSet.getBoolean("indisvalid")
                        }
                    }
                }

                indexState shouldBe ("idx_outbox_ready_message_type_external_ref" to true)
            }
        }
    })
