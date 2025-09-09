package no.nav.syfo.application.valkey

data class ValkeyEnvironment(
    val valkeyHost: String,
    val valkeyPort: Int,
    val username: String,
    val password: String,
)
