package no.nav.syfo.foresporsel.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.foresporsel.domain.PersistedForesporsel
import no.nav.syfo.sykmelding.db.FORESPORSEL_GRACE_PERIOD_DAYS
import java.sql.ResultSet
import java.util.UUID

class ForesporselOppfolgingsplanRepository(
    private val database: DatabaseInterface,
) {
    fun storeIfNotRecentlyRequested(
        sykmeldtFnr: String,
        narmesteLederFnr: String,
        organisasjonsnummer: String,
    ): UUID? {
        val statement = """
            WITH advisory_lock AS (
                SELECT pg_advisory_xact_lock(hashtext(?), hashtext(?))
            )
            INSERT INTO foresporsel_oppfolgingsplan (
                sykmeldt_fnr,
                narmeste_leder_fnr,
                organisasjonsnummer
            )
            SELECT ?, ?, ?
            FROM advisory_lock
            WHERE NOT EXISTS (
                SELECT 1
                FROM foresporsel_oppfolgingsplan
                WHERE sykmeldt_fnr = ?
                  AND narmeste_leder_fnr = ?
                  AND created_at > NOW() - (? * INTERVAL '1 day')
            )
            RETURNING id
        """.trimIndent()

        return database.connection.use { connection ->
            connection.prepareStatement(statement).use { preparedStatement ->
                preparedStatement.setString(1, sykmeldtFnr)
                preparedStatement.setString(2, narmesteLederFnr)
                preparedStatement.setString(3, sykmeldtFnr)
                preparedStatement.setString(4, narmesteLederFnr)
                preparedStatement.setString(5, organisasjonsnummer)
                preparedStatement.setString(6, sykmeldtFnr)
                preparedStatement.setString(7, narmesteLederFnr)
                preparedStatement.setLong(8, FORESPORSEL_GRACE_PERIOD_DAYS)

                preparedStatement.executeQuery().use { resultSet ->
                    val insertedId = if (resultSet.next()) {
                        resultSet.getObject("id", UUID::class.java)
                    } else {
                        null
                    }
                    connection.commit()
                    insertedId
                }
            }
        }
    }

    fun findForesporselForSykmeldt(
        sykmeldtFnr: String,
    ): List<PersistedForesporsel> {
        val statement = """
            SELECT id, sykmeldt_fnr, narmeste_leder_fnr, organisasjonsnummer, created_at
            FROM foresporsel_oppfolgingsplan
            WHERE sykmeldt_fnr = ?
            ORDER BY created_at DESC
        """.trimIndent()

        return database.connection.use { connection ->
            connection.prepareStatement(statement).use { preparedStatement ->
                preparedStatement.setString(1, sykmeldtFnr)
                preparedStatement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(resultSet.toPersistedForesporsel())
                        }
                    }
                }
            }
        }
    }
}

private fun ResultSet.toPersistedForesporsel(): PersistedForesporsel = PersistedForesporsel(
    id = getObject("id", UUID::class.java),
    sykmeldtFnr = getString("sykmeldt_fnr"),
    narmesteLederFnr = getString("narmeste_leder_fnr"),
    organisasjonsnummer = getString("organisasjonsnummer"),
    createdAt = getTimestamp("created_at").toInstant(),
)
