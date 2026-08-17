package no.nav.syfo.sykmelding.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.sykmelding.db.domain.PersistedSykmeldingsperiode
import no.nav.syfo.sykmelding.db.domain.SykmeldingsperiodeToStore
import java.sql.Date
import java.sql.ResultSet
import java.sql.Statement
import java.time.LocalDate
import java.util.UUID

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

    fun findEarliestSykmeldingsperiode(
        sykmeldtFnr: String,
        organisasjonsnummer: String,
        today: LocalDate,
    ): PersistedSykmeldingsperiode? {
        val lookbackDate = today.minusDays(FIND_EARLIEST_FOM_LOOKBACK_DAYS.toLong())
        val statement = """
            SELECT *
            FROM sykmeldingsperiode
            WHERE sykmeldt_fnr = ?
              AND organisasjonsnummer = ?
              AND invalidated_at IS NULL
              AND fom <= ?
              AND tom >= ?
            ORDER BY tom DESC, fom DESC
        """.trimIndent()

        val sykmeldingsperioder = database.connection.use { connection ->
            var idx = 0
            connection.prepareStatement(statement).use { preparedStatement ->
                preparedStatement.setString(++idx, sykmeldtFnr)
                preparedStatement.setString(++idx, organisasjonsnummer)
                preparedStatement.setDate(++idx, Date.valueOf(today))
                preparedStatement.setDate(++idx, Date.valueOf(lookbackDate))
                preparedStatement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(resultSet.toPersistedSykmeldingsperiode())
                        }
                    }
                }
            }
        }

        return sykmeldingsperioder.findEarliestContinuousSykmeldingsperiode(today)
    }

    private companion object {
        const val FIND_EARLIEST_FOM_LOOKBACK_DAYS = 50
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

private fun List<PersistedSykmeldingsperiode>.findEarliestContinuousSykmeldingsperiode(
    today: LocalDate,
): PersistedSykmeldingsperiode? {
    val periodsSortedForBackwardTraversal = sortedWith(
        compareByDescending<PersistedSykmeldingsperiode> { it.tom }.thenByDescending { it.fom },
    )

    val activePeriods = periodsSortedForBackwardTraversal.filter { period ->
        !period.fom.isAfter(today) && !period.tom.isBefore(today)
    }
    if (activePeriods.isEmpty()) {
        return null
    }

    var earliestPeriod = activePeriods.minBy { it.fom }

    for (period in periodsSortedForBackwardTraversal) {
        if (period.fom.isBefore(earliestPeriod.fom) && !period.tom.plusDays(1).isBefore(earliestPeriod.fom)) {
            earliestPeriod = period
        }
    }

    return earliestPeriod
}
