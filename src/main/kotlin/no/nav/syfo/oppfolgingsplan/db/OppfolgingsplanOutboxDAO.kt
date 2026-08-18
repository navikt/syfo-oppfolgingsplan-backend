package no.nav.syfo.oppfolgingsplan.db

import no.nav.syfo.application.database.DatabaseInterface
import java.util.UUID

fun DatabaseInterface.findOppfolgingsplanVarselSource(
    oppfolgingsplanUuid: UUID,
): OppfolgingsplanVarselSource = connection.use { connection ->
    connection.prepareStatement(
        """
        SELECT
            sykmeldt_fnr,
            skjult_fra IS NULL AND feilregistrert IS NULL AS eligible
        FROM oppfolgingsplan
        WHERE uuid = ?
        """.trimIndent(),
    ).use { statement ->
        statement.setObject(1, oppfolgingsplanUuid)
        statement.executeQuery().use { resultSet ->
            when {
                !resultSet.next() -> OppfolgingsplanVarselSource.NotFound
                !resultSet.getBoolean("eligible") -> OppfolgingsplanVarselSource.NoLongerEligible
                else -> OppfolgingsplanVarselSource.Eligible(
                    OppfolgingsplanVarselRecipient(resultSet.getString("sykmeldt_fnr")),
                )
            }
        }
    }
}

sealed interface OppfolgingsplanVarselSource {
    data class Eligible(val recipient: OppfolgingsplanVarselRecipient) : OppfolgingsplanVarselSource

    data object NotFound : OppfolgingsplanVarselSource

    data object NoLongerEligible : OppfolgingsplanVarselSource
}

@JvmInline
value class OppfolgingsplanVarselRecipient(
    val sykmeldtFnr: String,
) {
    override fun toString(): String = "OppfolgingsplanVarselRecipient()"
}
