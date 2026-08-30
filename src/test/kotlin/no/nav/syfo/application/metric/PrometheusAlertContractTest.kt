package no.nav.syfo.application.metric

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files
import java.nio.file.Path

class PrometheusAlertContractTest :
    FunSpec({
        val environments =
            mapOf(
                "dev" to mapOf(DESERIALIZATION_ALERT to "warning", RUNTIME_ALERT to "warning"),
                "prod" to mapOf(DESERIALIZATION_ALERT to "warning", RUNTIME_ALERT to "warning"),
            )

        environments.forEach { (environment, severities) ->
            val rules = Files.readString(Path.of("nais", "alerts-$environment.yaml"))

            test("$environment sykmelding consumer alerts keep their event and recovery semantics") {
                val deserialization = alertBlock(rules, DESERIALIZATION_ALERT)
                expression(deserialization) shouldBe DESERIALIZATION_EXPRESSION
                duration(deserialization) shouldBe "1m"
                severity(deserialization) shouldBe severities.getValue(DESERIALIZATION_ALERT)

                val runtime = alertBlock(rules, RUNTIME_ALERT)
                expression(runtime) shouldBe RUNTIME_EXPRESSION
                duration(runtime) shouldBe "15m"
                severity(runtime) shouldBe severities.getValue(RUNTIME_ALERT)

                deserialization shouldNotContain "rate("
                runtime shouldNotContain "rate("
            }
        }
    })

private fun alertBlock(
    rules: String,
    alert: String,
): String = Regex("""(?ms)^        - alert: ${Regex.escape(alert)}\n.*?(?=^        - alert:|\z)""")
    .find(rules)
    ?.value
    ?: error("Alert $alert is missing")

private fun expression(block: String): String = field(block, "expr")

private fun duration(block: String): String = field(block, "for")

private fun severity(block: String): String = field(block, "severity")

private fun field(
    block: String,
    name: String,
): String = Regex("""(?m)^\s+$name: (.+)$""")
    .find(block)
    ?.groupValues
    ?.get(1)
    ?: error("Field $name is missing")

private const val DESERIALIZATION_ALERT = "SykmeldingConsumerDeserializationErrors"
private const val RUNTIME_ALERT = "SykmeldingConsumerRuntimeErrors"
private const val DESERIALIZATION_EXPRESSION =
    """sum by (app) (increase(syfo_oppfolgingsplan_backend_sykmelding_deserialization_error_total{app="syfo-oppfolgingsplan-backend",namespace="team-esyfo"}[15m])) > 0"""
private const val RUNTIME_EXPRESSION =
    """sum by (app) (increase(syfo_oppfolgingsplan_backend_sykmelding_runtime_error_total{app="syfo-oppfolgingsplan-backend",namespace="team-esyfo"}[7m])) > 0"""
