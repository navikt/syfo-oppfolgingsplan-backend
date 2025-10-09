package no.nav.syfo.application.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.flywaydb.core.Flyway
import java.sql.Connection

data class DatabaseConfig(
    val jdbcUrl: String,
    val password: String,
    val username: String,
    val poolSize: Int = 4,
)

class Database(
    private val config: DatabaseConfig
) : DatabaseInterface {
    override val connection: Connection
        get() = dataSource.connection

    private var dataSource: HikariDataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            username = config.username
            password = config.password
            maximumPoolSize = config.poolSize
            minimumIdle = 1
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            metricRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
            validate()
        }
    )

    init {
        runFlywayMigrations()
    }

    private fun runFlywayMigrations() = Flyway.configure().run {
        dataSource(
            config.jdbcUrl,
            config.username,
            config.password,
        )
            .locations("classpath:db/migration")
            .failOnMissingLocations(false)
        load().migrate().migrationsExecuted
    }
}

interface DatabaseInterface {
    val connection: Connection
}
