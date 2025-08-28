package no.nav.syfo.application

import no.nav.syfo.application.database.DatabaseEnvironment
import no.nav.syfo.application.kafka.KafkaEnv
import no.nav.syfo.texas.TexasEnvironment

const val NAIS_DATABASE_ENV_PREFIX = "OPPFOLGINGSPLAN_DB"

interface Environment {
    val database: DatabaseEnvironment
    val texas: TexasEnvironment
    val dineSykmeldteBaseUrl: String
    val dokarkivBaseUrl: String
    val dokarkivScope: String
    val pdfGenUrl: String
    val isDialogmeldingBaseUrl: String
    val isTilgangskontrollBaseUrl: String
    val pdlBaseUrl: String
    val pdlScope: String
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
        tokenEndpoint = getEnvVar("NAIS_TOKEN_ENDPOINT"),
        exchangeTargetDineSykmeldte = "${getEnvVar("NAIS_CLUSTER_NAME")}:team-esyfo:dinesykmeldte-backend",
        exchangeTargetIsDialogmelding = "${getEnvVar("NAIS_CLUSTER_NAME")}:teamsykefravr:isdialogmelding",
        exchangeTargetIsTilgangskontroll = "${getEnvVar("NAIS_CLUSTER_NAME")}:teamsykefravr:istilgangskontroll",
    ),

    override val pdfGenUrl: String = getEnvVar("PDFGEN_BASE_URL"),
    override val dineSykmeldteBaseUrl: String = getEnvVar("DINE_SYKMELDTE_BASE_URL"),
    override val dokarkivBaseUrl: String = getEnvVar("DOKARKIV_URL"),
    override val dokarkivScope: String = getEnvVar("DOKARKIV_SCOPE"),
    override val isDialogmeldingBaseUrl: String = getEnvVar("ISDIALOGMELDING_BASE_URL"),
    override val isTilgangskontrollBaseUrl: String = getEnvVar("ISTILGANGSKONTROLL_URL"),
    override val pdlBaseUrl: String = getEnvVar("PDL_BASE_URL"),
    override val pdlScope: String = getEnvVar("PDL_SCOPE"),
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
        name = "syfo-oppfolgingsplan-backend_dev",
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
        tokenEndpoint = "http://localhost:3000/api/v1/token",
        exchangeTargetDineSykmeldte = "dev-gcp:team-esyfo:dinesykmeldte-backend",
        exchangeTargetIsDialogmelding = "dev-gcp:teamsykefravr:isdialogmelding",
        exchangeTargetIsTilgangskontroll = "dev-gcp:teamsykefravr:istilgangskontroll",
    ),
    override val dineSykmeldteBaseUrl: String = "https://dinesykmeldte-backend.dev.intern.nav.no",
    override val dokarkivScope: String = "dokarkiv",
    override val dokarkivBaseUrl: String = "https://isdialogmelding.intern.dev.nav.no",
    override val isDialogmeldingBaseUrl: String = "https://isdialogmelding.intern.dev.nav.no",
    override val isTilgangskontrollBaseUrl: String = "https://istilgangskontroll.intern.dev.nav.no",
    override val pdfGenUrl: String = "http://localhost:9091",
    override val pdlBaseUrl: String = "https://pdl-api.dev.intern.nav.no",
    override val pdlScope: String = "pdl",
    override val kafka: KafkaEnv = KafkaEnv.createForLocal()
) : Environment
