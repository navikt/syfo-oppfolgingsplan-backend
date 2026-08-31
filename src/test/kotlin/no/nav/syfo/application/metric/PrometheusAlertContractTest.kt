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
        val outboxExpressions =
            mapOf(
                OUTBOX_OLDEST_DUE_ALERT to outboxExpression("outbox_oldest_due_age_seconds", "900"),
                OUTBOX_EXPIRED_CLAIMS_ALERT to outboxExpression("outbox_expired_claims", "0"),
                OUTBOX_PERSISTENT_FAILURES_ALERT to outboxExpression("outbox_retrying", "0"),
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

            test("$environment outbox alerts filter each pod snapshot for freshness before max aggregation") {
                outboxExpressions.forEach { (alert, expectedExpression) ->
                    expression(alertBlock(rules, alert)) shouldBe expectedExpression
                }
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
private const val OUTBOX_OLDEST_DUE_ALERT = "OppfolgingsplanOutboxOldestDueTooOld"
private const val OUTBOX_EXPIRED_CLAIMS_ALERT = "OppfolgingsplanOutboxExpiredClaims"
private const val OUTBOX_PERSISTENT_FAILURES_ALERT = "OppfolgingsplanOutboxPersistentFailures"
private const val DESERIALIZATION_EXPRESSION =
    """sum by (app) (increase(syfo_oppfolgingsplan_backend_sykmelding_deserialization_error_total{app="syfo-oppfolgingsplan-backend",namespace="team-esyfo"}[15m])) > 0"""
private const val RUNTIME_EXPRESSION =
    """sum by (app) (increase(syfo_oppfolgingsplan_backend_sykmelding_runtime_error_total{app="syfo-oppfolgingsplan-backend",namespace="team-esyfo"}[7m])) > 0"""

private fun outboxExpression(
    metricSuffix: String,
    threshold: String,
): String = "max by (app, message_type) (" +
    "syfo_oppfolgingsplan_backend_$metricSuffix$OUTBOX_SELECTOR " +
    "and on (pod, message_type) " +
    "(time() - $OUTBOX_FRESHNESS_METRIC$OUTBOX_SELECTOR < 180)) > $threshold"

private const val OUTBOX_SELECTOR =
    """{app="syfo-oppfolgingsplan-backend",namespace="team-esyfo",message_type=~"OPPFOLGINGSPLAN_.*"}"""
private const val OUTBOX_FRESHNESS_METRIC =
    "syfo_oppfolgingsplan_backend_outbox_queue_snapshot_last_success_timestamp_seconds"
