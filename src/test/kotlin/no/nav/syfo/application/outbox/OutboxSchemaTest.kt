package no.nav.syfo.application.outbox

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import no.nav.syfo.TestDB
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import java.sql.DriverManager

class OutboxSchemaTest :
    DescribeSpec({
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
                    "scheduled_at",
                    "status",
                    "attempt_count",
                    "last_attempt_at",
                    "created_at",
                    "sent_at",
                    "cancellation_reason",
                )
                columns["uuid"] shouldBe false
                columns["payload"] shouldBe false
                columns["sent_at"] shouldBe true
                columns["cancellation_reason"] shouldBe true
                indexes shouldContainAll setOf("uq_outbox_message", "idx_outbox_ready")
            }

            it("safely replaces the abandoned dev pilot after its history rows are removed") {
                val isolatedDatabase = TestDB.createIsolatedDatabase()
                with(isolatedDatabase) {
                    val flywayConfiguration = Flyway.configure()
                        .locations("db")
                        .dataSource(jdbcUrl, username, password)
                    flywayConfiguration.target(MigrationVersion.fromVersion("25")).load().migrate()

                    DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
                        connection.createStatement().use { statement ->
                            statement.execute(abandonedNotificationPilotSql)
                            statement.execute(
                                """
                                DELETE FROM flyway_schema_history
                                WHERE version IN ('25.1', '26', '27')
                                """.trimIndent(),
                            )
                        }
                    }

                    Flyway.configure()
                        .locations("db")
                        .dataSource(jdbcUrl, username, password)
                        .load()
                        .migrate()

                    DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
                        connection.createStatement().use { statement ->
                            statement.executeQuery(
                                """
                                SELECT column_default
                                FROM information_schema.columns
                                WHERE table_schema = current_schema()
                                  AND table_name = 'oppfolgingsplan'
                                  AND column_name = 'event_id'
                                """.trimIndent(),
                            ).use { resultSet ->
                                resultSet.next() shouldBe true
                                resultSet.getString("column_default") shouldBe null
                            }
                            statement.executeQuery(
                                """
                                SELECT version, success
                                FROM flyway_schema_history
                                ORDER BY installed_rank DESC
                                LIMIT 1
                                """.trimIndent(),
                            ).use { resultSet ->
                                resultSet.next() shouldBe true
                                resultSet.getString("version") shouldBe "26"
                                resultSet.getBoolean("success") shouldBe true
                            }
                        }
                    }
                }
            }
        }
    })

private val abandonedNotificationPilotSql =
    """
    CREATE TABLE outbox
    (
        uuid UUID PRIMARY KEY,
        message_type TEXT NOT NULL,
        dedup_key TEXT NOT NULL,
        external_ref TEXT NOT NULL,
        payload JSONB NOT NULL DEFAULT '{}'::jsonb,
        scheduled_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        status TEXT NOT NULL DEFAULT 'READY',
        attempt_count INTEGER NOT NULL DEFAULT 0,
        last_attempt_at TIMESTAMPTZ,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        sent_at TIMESTAMPTZ,
        CONSTRAINT uq_outbox_message UNIQUE (message_type, dedup_key),
        CONSTRAINT chk_outbox_status CHECK (status IN ('READY', 'SENT', 'IRRELEVANT', 'FAILED')),
        CONSTRAINT chk_outbox_attempt_count CHECK (attempt_count >= 0)
    );

    ALTER TABLE oppfolgingsplan
        ALTER COLUMN event_id SET DEFAULT gen_random_uuid();

    INSERT INTO flyway_schema_history
        (installed_rank, version, description, type, script, installed_by, execution_time, success)
    VALUES
        (100, '25.1', 'remove abandoned outbox pilot', 'SQL',
         'V25_1__remove_abandoned_outbox_pilot.sql', current_user, 1, TRUE),
        (101, '26', 'create notification outbox', 'SQL',
         'V26__create_notification_outbox.sql', current_user, 1, TRUE),
        (102, '27', 'ensure outbox rolling deployment compatibility', 'SQL',
         'V27__ensure_outbox_rolling_deployment_compatibility.sql', current_user, 1, TRUE);
    """.trimIndent()
