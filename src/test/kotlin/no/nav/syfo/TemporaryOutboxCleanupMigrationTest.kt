package no.nav.syfo

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import java.sql.DriverManager
import java.util.UUID

class TemporaryOutboxCleanupMigrationTest :
    DescribeSpec({
        it("removes the empty abandoned notification pilot") {
            val isolatedDatabase = createIsolatedDatabase()
            with(isolatedDatabase) {
                migrateToPilotState(this, withMessage = false)

                Flyway.configure()
                    .locations("db")
                    .dataSource(jdbcUrl, username, password)
                    .load()
                    .migrate()

                DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
                    connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT to_regclass('outbox')").use { resultSet ->
                            resultSet.next() shouldBe true
                            resultSet.getString(1) shouldBe null
                        }
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
                    }
                }
            }
        }

        it("removes a non-empty pilot after verifying its exact signature") {
            val isolatedDatabase = createIsolatedDatabase()
            with(isolatedDatabase) {
                migrateToPilotState(this, withMessage = true)

                Flyway.configure()
                    .locations("db")
                    .dataSource(jdbcUrl, username, password)
                    .load()
                    .migrate()

                DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
                    connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT to_regclass('public.outbox')").use { resultSet ->
                            resultSet.next() shouldBe true
                            resultSet.getString(1) shouldBe null
                        }
                        statement.executeQuery("SELECT to_regclass('abandoned_outbox_pilot.outbox')").use { resultSet ->
                            resultSet.next() shouldBe true
                            resultSet.getString(1) shouldBe null
                        }
                    }
                }
            }
        }

        it("refuses to drop an outbox that is not the abandoned pilot") {
            val isolatedDatabase = createIsolatedDatabase()
            with(isolatedDatabase) {
                migrateToPilotState(
                    database = this,
                    withMessage = true,
                    pilotSql = abandonedPilotSql.replace(", 'FAILED'", ""),
                )

                shouldThrow<Exception> {
                    Flyway.configure()
                        .locations("db")
                        .dataSource(jdbcUrl, username, password)
                        .load()
                        .migrate()
                }

                DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
                    connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT to_regclass('public.outbox')").use { resultSet ->
                            resultSet.next() shouldBe true
                            resultSet.getString(1) shouldBe "outbox"
                        }
                        statement.executeQuery("SELECT count(*) FROM public.outbox").use { resultSet ->
                            resultSet.next() shouldBe true
                            resultSet.getLong(1) shouldBe 1L
                        }
                    }
                }
            }
        }
    })

private fun migrateToPilotState(
    database: IsolatedDatabase,
    withMessage: Boolean,
    pilotSql: String = abandonedPilotSql,
) = with(database) {
    val configuration = Flyway.configure()
        .locations("db")
        .dataSource(jdbcUrl, username, password)
    configuration.target(MigrationVersion.fromVersion("25")).load().migrate()
    DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
        connection.createStatement().use { statement ->
            statement.execute(pilotSql)
            if (withMessage) {
                statement.execute(
                    """
                    INSERT INTO outbox (uuid, message_type, dedup_key, external_ref)
                    VALUES (
                        '00000000-0000-0000-0000-000000000001',
                        'OPPFOLGINGSPLAN_OPPRETTET',
                        '00000000-0000-0000-0000-000000000002',
                        '00000000-0000-0000-0000-000000000002'
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}

private val abandonedPilotSql =
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
    """.trimIndent()

private data class IsolatedDatabase(
    val jdbcUrl: String,
    val username: String,
    val password: String,
)

private fun createIsolatedDatabase(): IsolatedDatabase {
    val databaseName = "cleanup_${UUID.randomUUID().toString().replace("-", "")}"
    val sourceUrl = TestDB.database.connection.use { connection ->
        val jdbcUrl = connection.metaData.url
        connection.autoCommit = true
        connection.createStatement().use { statement ->
            statement.execute("CREATE DATABASE $databaseName")
        }
        jdbcUrl
    }
    return IsolatedDatabase(
        jdbcUrl = sourceUrl.substringBeforeLast('/') + "/$databaseName",
        username = "username",
        password = "password",
    )
}
