package no.nav.syfo

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.flywaydb.core.Flyway
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import java.sql.Connection
import java.sql.DriverManager

private const val ABANDONED_CHECKSUM = -380080342

/**
 * Midlertidig test som foelger afterMigrate-callbacken. Skal ikke merges til main.
 */
class AbandonedOutboxCleanupCallbackTest :
    DescribeSpec({
        describe("afterMigrate cleanup av forlatt dev-outbox") {
            it("fjerner den forlatte piloten og historikkraden") {
                withIsolatedDatabase { connection, migrate ->
                    migrate()
                    connection.installAbandonedPilot()

                    migrate()

                    connection.tableExists("outbox") shouldBe false
                    connection.abandonedHistoryRowCount() shouldBe 0
                }
            }

            it("lar en korrekt outbox med completed_at staa urort") {
                withIsolatedDatabase { connection, migrate ->
                    migrate()
                    connection.execute(
                        """
                        CREATE TABLE outbox (
                            uuid UUID PRIMARY KEY,
                            completed_at TIMESTAMPTZ
                        )
                        """,
                    )

                    migrate()

                    connection.tableExists("outbox") shouldBe true
                }
            }

            it("lar V26 fra #408 kjoere rent etterpaa") {
                withIsolatedDatabase { connection, migrate ->
                    migrate()
                    connection.installAbandonedPilot()
                    migrate()

                    // Samme migrate som #408 kjoerer ved oppstart, med den nye V26 paa plass.
                    Flyway.configure()
                        .locations("db", "db-pr408")
                        .configuration(mapOf("flyway.postgresql.transactional.lock" to "false"))
                        .dataSource(connection.jdbcUrlOfSelf(), "username", "password")
                        .load()
                        .migrate()

                    connection.columnExists("outbox", "completed_at") shouldBe true
                    connection.columnExists("outbox", "sent_at") shouldBe false
                }
            }

            it("er idempotent naar det ikke finnes noen forlatt pilot") {
                withIsolatedDatabase { _, migrate ->
                    migrate()
                    migrate()
                    migrate()
                }
            }
        }
    })

private val cleanupContainer: PsqlContainer by lazy {
    PsqlContainer()
        .withExposedPorts(5432)
        .withUsername("username")
        .withPassword("password")
        .withDatabaseName("database")
        .also {
            it.waitingFor(HostPortWaitStrategy())
            it.start()
        }
}

private fun withIsolatedDatabase(block: (Connection, () -> Unit) -> Unit) {
    val databaseName = "cleanup_${System.nanoTime()}"
    DriverManager.getConnection(cleanupContainer.jdbcUrl, "username", "password").use { admin ->
        admin.createStatement().use { it.execute("CREATE DATABASE $databaseName") }
    }

    val jdbcUrl = cleanupContainer.jdbcUrl.replace("/database", "/$databaseName")
    val migrate = {
        Flyway.configure()
            .locations("db")
            .configuration(mapOf("flyway.postgresql.transactional.lock" to "false"))
            .dataSource(jdbcUrl, "username", "password")
            .load()
            .migrate()
        Unit
    }

    DriverManager.getConnection(jdbcUrl, "username", "password").use { connection ->
        block(connection, migrate)
    }
}

/** Gjenskaper dev-tilstanden fra deployen 2026-08-13: pilotskjema med sent_at og flyway-rad 26. */
private fun Connection.installAbandonedPilot() {
    execute(
        """
        CREATE TABLE outbox (
            uuid UUID PRIMARY KEY,
            message_type TEXT NOT NULL,
            dedup_key TEXT NOT NULL,
            external_ref TEXT NOT NULL,
            payload JSONB NOT NULL,
            available_at TIMESTAMPTZ NOT NULL,
            status TEXT NOT NULL DEFAULT 'READY',
            claim_token UUID,
            lease_until TIMESTAMPTZ,
            failure_count INTEGER NOT NULL DEFAULT 0,
            last_failure_at TIMESTAMPTZ,
            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            sent_at TIMESTAMPTZ,
            cancellation_reason TEXT
        )
        """,
    )
    execute(
        """
        INSERT INTO flyway_schema_history
            (installed_rank, version, description, type, script, checksum,
             installed_by, execution_time, success)
        SELECT max(installed_rank) + 1, '26', 'create generic outbox', 'SQL',
               'V26__create_generic_outbox.sql', $ABANDONED_CHECKSUM, 'test', 72, true
        FROM flyway_schema_history
        """,
    )
}

private fun Connection.abandonedHistoryRowCount(): Int =
    prepareStatement(
        "SELECT count(*) FROM flyway_schema_history WHERE version = '26' AND checksum = $ABANDONED_CHECKSUM",
    ).use { statement ->
        statement.executeQuery().use { rows ->
            rows.next()
            rows.getInt(1)
        }
    }

private fun Connection.tableExists(name: String): Boolean =
    prepareStatement("SELECT to_regclass('public.$name') IS NOT NULL").use { statement ->
        statement.executeQuery().use { rows ->
            rows.next()
            rows.getBoolean(1)
        }
    }

private fun Connection.columnExists(table: String, column: String): Boolean =
    prepareStatement(
        """
        SELECT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
        )
        """,
    ).use { statement ->
        statement.setString(1, table)
        statement.setString(2, column)
        statement.executeQuery().use { rows ->
            rows.next()
            rows.getBoolean(1)
        }
    }

private fun Connection.jdbcUrlOfSelf(): String = metaData.url

private fun Connection.execute(sql: String) = createStatement().use { it.execute(sql) }
