package no.nav.syfo.oppfolgingsplan.db

import no.nav.syfo.application.database.DatabaseInterface
import java.util.UUID

fun DatabaseInterface.findOppfolgingsplanVarselRecipient(
    oppfolgingsplanUuid: UUID,
): OppfolgingsplanVarselRecipient? = connection.use { connection ->
    connection.prepareStatement(
        """
        SELECT sykmeldt_fnr
        FROM oppfolgingsplan
        WHERE uuid = ?
          AND skjult_fra IS NULL
          AND feilregistrert IS NULL
        """.trimIndent(),
    ).use { statement ->
        statement.setObject(1, oppfolgingsplanUuid)
        statement.executeQuery().use { resultSet ->
            if (resultSet.next()) {
                OppfolgingsplanVarselRecipient(resultSet.getString("sykmeldt_fnr"))
            } else {
                null
            }
        }
    }
}

@JvmInline
value class OppfolgingsplanVarselRecipient(
    val sykmeldtFnr: String,
) {
    override fun toString(): String = "OppfolgingsplanVarselRecipient()"
}
