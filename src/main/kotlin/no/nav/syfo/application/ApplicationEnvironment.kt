package no.nav.syfo.application

import no.nav.syfo.application.database.DatabaseEnvironment
import no.nav.syfo.application.kafka.KafkaEnv
import no.nav.syfo.texas.TexasEnvironment

const val NAIS_DATABASE_ENV_PREFIX = "OPPFOLGINGSPLAN_DB"

interface Environment {
    val database: DatabaseEnvironment
    val texas: TexasEnvironment
    val dineSykmeldteBaseUrl: String
    val pdfGenUrl: String
    val isDialogmeldingBaseUrl: String
    val kafka: KafkaEnv
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
        tokenExchangeEndpoint = getEnvVar("NAIS_TOKEN_EXCHANGE_ENDPOINT"),
        exchangeTargetDineSykmeldte = "${getEnvVar("NAIS_CLUSTER_NAME")}:team-esyfo:dinesykmeldte-backend",
        exchangeTargetIsDialogmelding = "${getEnvVar("NAIS_CLUSTER_NAME")}:teamsykefravr:isdialogmelding"
    ),

    override val pdfGenUrl: String = getEnvVar("PDFGEN_BASE_URL"),
    override val dineSykmeldteBaseUrl: String = getEnvVar("DINE_SYKMELDTE_BASE_URL"),
    override val isDialogmeldingBaseUrl: String = getEnvVar("ISDIALOGMELDING_BASE_URL"),
    override val kafka: KafkaEnv = KafkaEnv.createFromEnvVars()
) : Environment

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")


fun isLocalEnv(): Boolean =
    getEnvVar("NAIS_CLUSTER_NAME", "local") == "local"

fun isProdEnv(): Boolean =
    getEnvVar("NAIS_CLUSTER_NAME", "local") == "prod-gcp"

data class LocalEnvironment(
    override val database: DatabaseEnvironment = DatabaseEnvironment(
        host = "localhost",
        port = "5432",
        name = "syfo-oppfolginsplan-backend_dev",
        username = "username",
        password = "password",
        sslcert = null,
        sslkey = "",
        sslrootcert = "",
        sslmode = "",
    ),
    override val texas: TexasEnvironment = TexasEnvironment(
        tokenIntrospectionEndpoint = "http://localhost:3000/api/v1/introspect",
        tokenExchangeEndpoint = "http://localhost:3000/api/v1/token/exchange",
        exchangeTargetDineSykmeldte = "dev-gcp:team-esyfo:dinesykmeldte-backend",
        exchangeTargetIsDialogmelding = "dev-gcp:teamsykefravr:isdialogmelding"
    ),
    override val dineSykmeldteBaseUrl: String = "https://dinesykmeldte-backend.dev.intern.nav.no",
    override val isDialogmeldingBaseUrl: String = "https://isdialogmelding.intern.dev.nav.no",
    override val pdfGenUrl: String = "http://localhost:9091",
    override val kafka: KafkaEnv = KafkaEnv.createForLocal()
) : Environment
