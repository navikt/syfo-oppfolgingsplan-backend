package no.nav.syfo.sykmelding.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.sykmelding.db.domain.PersistedSykmeldingsperiode
import no.nav.syfo.sykmelding.db.domain.SykmeldingsperiodeToStore
import java.sql.Date
import java.sql.ResultSet
import java.sql.Statement
import java.util.UUID

const val FORESPORSEL_GRACE_PERIOD_DAYS = 16L

class SykmeldingsperiodeRepository(
    private val database: DatabaseInterface,
) {
    fun storeSykmeldingsperioder(
        sykmeldingsperioder: List<SykmeldingsperiodeToStore>,
    ): Int {
        if (sykmeldingsperioder.isEmpty()) {
            return 0
        }

        val statement = """
            INSERT INTO sykmeldingsperiode (
                sykmeldt_fnr,
                organisasjonsnummer,
                sykmelding_id,
                fom,
                tom
            ) VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (sykmelding_id, fom, tom) DO NOTHING
        """.trimIndent()

        return database.connection.use { connection ->
            val insertedCount = connection.prepareStatement(statement).use { preparedStatement ->
                sykmeldingsperioder.forEach { sykmeldingsperiode ->
                    preparedStatement.setString(1, sykmeldingsperiode.sykmeldtFnr)
                    preparedStatement.setString(2, sykmeldingsperiode.organisasjonsnummer)
                    preparedStatement.setString(3, sykmeldingsperiode.sykmeldingId)
                    preparedStatement.setDate(4, Date.valueOf(sykmeldingsperiode.fom))
                    preparedStatement.setDate(5, Date.valueOf(sykmeldingsperiode.tom))
                    preparedStatement.addBatch()
                }

                preparedStatement.executeBatch().count { result ->
                    result > 0 || result == Statement.SUCCESS_NO_INFO
                }
            }
            connection.commit()
            insertedCount
        }
    }

    fun invalidateSykmelding(
        sykmeldingId: String,
    ): Int {
        val statement = """
            UPDATE sykmeldingsperiode
            SET invalidated_at = NOW()
            WHERE sykmelding_id = ?
              AND invalidated_at IS NULL
        """.trimIndent()

        return database.connection.use { connection ->
            val updatedRows = connection.prepareStatement(statement).use { preparedStatement ->
                preparedStatement.setString(1, sykmeldingId)
                preparedStatement.executeUpdate()
            }
            connection.commit()
            updatedRows
        }
    }

    fun findBySykmeldingId(
        sykmeldingId: String,
    ): List<PersistedSykmeldingsperiode> {
        val statement = """
            SELECT *
            FROM sykmeldingsperiode
            WHERE sykmelding_id = ?
            ORDER BY fom, tom
        """.trimIndent()

        return database.connection.use { connection ->
            connection.prepareStatement(statement).use { preparedStatement ->
                preparedStatement.setString(1, sykmeldingId)
                preparedStatement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(resultSet.toPersistedSykmeldingsperiode())
                        }
                    }
                }
            }
        }
    }

    fun findOrganisasjonerMedAktivSykmeldingsperiode(
        sykmeldtFnr: String,
    ): List<String> {
        val statement = """
            SELECT DISTINCT organisasjonsnummer
            FROM sykmeldingsperiode
            WHERE sykmeldt_fnr = ?
              AND fom <= CURRENT_DATE
              AND tom + (? * INTERVAL '1 day') >= CURRENT_DATE
              AND invalidated_at IS NULL
        """.trimIndent()

        return database.connection.use { connection ->
            connection.prepareStatement(statement).use { preparedStatement ->
                preparedStatement.setString(1, sykmeldtFnr)
                preparedStatement.setLong(2, FORESPORSEL_GRACE_PERIOD_DAYS)
                preparedStatement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(resultSet.getString("organisasjonsnummer"))
                        }
                    }
                }
            }
        }
    }
}

private fun ResultSet.toPersistedSykmeldingsperiode(): PersistedSykmeldingsperiode = PersistedSykmeldingsperiode(
    id = getObject("id", UUID::class.java),
    sykmeldtFnr = getString("sykmeldt_fnr"),
    organisasjonsnummer = getString("organisasjonsnummer"),
    sykmeldingId = getString("sykmelding_id"),
    fom = getDate("fom").toLocalDate(),
    tom = getDate("tom").toLocalDate(),
    invalidatedAt = getTimestamp("invalidated_at")?.toInstant(),
    createdAt = getTimestamp("created_at").toInstant(),
)
