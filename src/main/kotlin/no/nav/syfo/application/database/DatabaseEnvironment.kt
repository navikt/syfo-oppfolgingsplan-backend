package no.nav.syfo.application.database

data class DatabaseEnvironment(
    val host: String,
    val port: String,
    val name: String,
    val username: String,
    val password: String,
    val sslcert: String?,
    val sslkey: String,
    val sslrootcert: String,
    val sslmode: String,
) {
    fun jdbcUrl(): String {
        val sslsuffix = if (sslcert == null ) "" else
            "?ssl=on&sslrootcert=$sslrootcert&sslcert=$sslcert&sslmode=$sslmode&sslkey=$sslkey"

        val url = "jdbc:postgresql://$host:$port/$name$sslsuffix"
        return url
    }
}
