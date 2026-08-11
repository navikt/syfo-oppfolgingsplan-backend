package no.nav.syfo.oppfolgingsplan.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.exposedTransaction
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.statements.StatementType

data class AktivPlanOrUtkastExists(
    val aktivPlanExists: Boolean,
    val utkastExists: Boolean,
)

/**
 * Existence check without loading rows. Mirrors the visibility rules of
 * findAllOppfolgingsplanerBy (skjult_fra/feilregistrert filtered) and
 * findOppfolgingsplanUtkastBy.
 */
suspend fun DatabaseInterface.existsAktivPlanOrUtkast(
    sykmeldtFnr: String,
    organisasjonsnummer: String,
): AktivPlanOrUtkastExists = exposedTransaction(readOnly = true) {
    val statement = """
        SELECT
            EXISTS (
                SELECT 1
                FROM oppfolgingsplan
                WHERE sykmeldt_fnr = ?
                  AND organisasjonsnummer = ?
                  AND skjult_fra IS NULL
                  AND feilregistrert IS NULL
            ) AS aktiv_plan_exists,
            EXISTS (
                SELECT 1
                FROM oppfolgingsplan_utkast
                WHERE sykmeldt_fnr = ?
                  AND organisasjonsnummer = ?
            ) AS utkast_exists
    """.trimIndent()

    exec(
        stmt = statement,
        args = listOf(
            TextColumnType() to sykmeldtFnr,
            TextColumnType() to organisasjonsnummer,
            TextColumnType() to sykmeldtFnr,
            TextColumnType() to organisasjonsnummer,
        ),
        explicitStatementType = StatementType.SELECT,
    ) { resultSet ->
        check(resultSet.next()) { "Existence query returned no result" }
        AktivPlanOrUtkastExists(
            aktivPlanExists = resultSet.getBoolean("aktiv_plan_exists"),
            utkastExists = resultSet.getBoolean("utkast_exists"),
        )
    } ?: error("Existence query returned no result set")
}
