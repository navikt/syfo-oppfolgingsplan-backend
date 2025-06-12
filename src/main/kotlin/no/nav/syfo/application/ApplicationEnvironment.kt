package no.nav.syfo.application

import io.ktor.server.application.*
import no.nav.syfo.application.database.DatabaseEnvironment
import no.nav.syfo.texas.TexasEnvironment

const val NAIS_DATABASE_ENV_PREFIX = "OPPFOLGINGSPLAN_DB"

interface Environment {
    val database: DatabaseEnvironment
    val texas: TexasEnvironment
    val dineSykmeldteBaseUrl: String
}

data class NaisEnvironment(
    override val database: DatabaseEnvironment = DatabaseEnvironment(
        host = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_HOST"),
        port = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_PORT"),
        name = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_DATABASE"),
        username = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_USERNAME"),
        password = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_PASSWORD"),
        sslcert = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_SSLCERT"),
        sslkey = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_SSLKEY_PK8"),
        sslrootcert = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_SSLROOTCERT"),
        sslmode = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_SSLMODE"),
    ),
    override val texas: TexasEnvironment = TexasEnvironment(
        tokenIntrospectionEndpoint = getEnvVar("NAIS_TOKEN_INTROSPECTION_ENDPOINT"),
    ),
    override val dineSykmeldteBaseUrl: String = getEnvVar("DINE_SYKMELDTE_BASE_URL"),
) : Environment

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

val Application.envKind get() = environment.config.property("ktor.environment").getString()

fun Application.isDev(): Boolean = (envKind == "development")


data class DevelopmentEnvironment(
    override val database: DatabaseEnvironment = DatabaseEnvironment(
        host = "localhost",
        port = "5432",
        name = "followupplan-backend_dev",
        username = "username",
        password = "password",
        sslcert = null,
        sslkey = "",
        sslrootcert = "",
        sslmode = "",
    ),
    override val texas: TexasEnvironment = TexasEnvironment(
        tokenIntrospectionEndpoint = "http://localhost:3000/api/v1/introspect",
    ),
    override val dineSykmeldteBaseUrl: String = "https://dinesykmeldte-backend.dev.intern.nav.no",
) : Environment
