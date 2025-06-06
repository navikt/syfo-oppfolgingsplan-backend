package no.nav.syfo.application.database


lateinit var applicationDatabase: DatabaseInterface
fun databaseModule(
    databaseEnvironment: DatabaseEnvironment
) {
    applicationDatabase = Database(
        DatabaseConfig(
            jdbcUrl = databaseEnvironment.jdbcUrl(),
            username = databaseEnvironment.username,
            password = databaseEnvironment.password,
        )
    )
}
