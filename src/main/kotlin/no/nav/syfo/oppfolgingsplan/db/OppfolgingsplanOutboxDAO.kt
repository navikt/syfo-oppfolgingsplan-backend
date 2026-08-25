package no.nav.syfo.oppfolgingsplan.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.outbox.domain.OutboxCancellationReason
import no.nav.syfo.oppfolgingsplan.outbox.OppfolgingsplanOutboxMessageType
import java.sql.Connection
import java.sql.Date
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

private val ZONE_OSLO: ZoneId = ZoneId.of("Europe/Oslo")

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

fun DatabaseInterface.findOppfolgingsplanEvalueringPaaminnelseSource(
    oppfolgingsplanUuid: UUID,
    clock: Clock = Clock.systemUTC(),
): OppfolgingsplanEvalueringPaaminnelseSource = connection.use { connection ->
    val today = LocalDate.now(clock.withZone(ZONE_OSLO))
    connection.prepareStatement(
        """
        SELECT
            oppfolgingsplan.sykmeldt_fnr,
            oppfolgingsplan.sykmeldt_full_name,
            oppfolgingsplan.organisasjonsnummer,
            oppfolgingsplan.organisasjonsnavn,
            oppfolgingsplan.evalueringsdato,
            EXISTS (
                SELECT 1
                FROM sykmeldingsperiode
                WHERE sykmeldingsperiode.sykmeldt_fnr = oppfolgingsplan.sykmeldt_fnr
                  AND sykmeldingsperiode.organisasjonsnummer = oppfolgingsplan.organisasjonsnummer
                  AND sykmeldingsperiode.invalidated_at IS NULL
                  AND sykmeldingsperiode.fom <= ?
                  AND sykmeldingsperiode.tom >= ?
            ) AS has_active_sykmeldingsperiode
        FROM oppfolgingsplan
        WHERE oppfolgingsplan.uuid = ?
        """.trimIndent(),
    ).use { statement ->
        var index = 0
        statement.setDate(++index, Date.valueOf(today))
        statement.setDate(++index, Date.valueOf(today))
        statement.setObject(++index, oppfolgingsplanUuid)
        statement.executeQuery().use { resultSet ->
            when {
                !resultSet.next() -> OppfolgingsplanEvalueringPaaminnelseSource.NotFound
                !resultSet.getBoolean("has_active_sykmeldingsperiode") -> OppfolgingsplanEvalueringPaaminnelseSource.NoLongerEligible
                else -> OppfolgingsplanEvalueringPaaminnelseSource.Eligible(
                    OppfolgingsplanEvalueringPaaminnelseSourceData(
                        sykmeldtFnr = resultSet.getString("sykmeldt_fnr"),
                        sykmeldtFullName = resultSet.getString("sykmeldt_full_name"),
                        organisasjonsnummer = resultSet.getString("organisasjonsnummer"),
                        organisasjonsnavn = resultSet.getString("organisasjonsnavn"),
                        evalueringsdato = resultSet.getDate("evalueringsdato").toLocalDate(),
                    ),
                )
            }
        }
    }
}

fun Connection.cancelReadySupersededEvalueringPaaminnelseRows(
    sykmeldtFnr: String,
    organisasjonsnummer: String,
    supersedingOppfolgingsplanUuid: UUID,
    completedAt: Instant,
): Map<OppfolgingsplanOutboxMessageType, Int> {
    val countByMessageType = OppfolgingsplanOutboxMessageType.evalueringPaaminnelseTypes
        .associateWith { 0 }
        .toMutableMap()

    prepareStatement(
        """
        WITH cancelled AS (
            UPDATE outbox
            SET status = 'CANCELLED',
                completed_at = ?,
                cancellation_reason = ?
            FROM oppfolgingsplan
            WHERE outbox.status = 'READY'
              AND outbox.message_type IN (?, ?)
              AND outbox.external_ref = oppfolgingsplan.uuid::text
              AND oppfolgingsplan.sykmeldt_fnr = ?
              AND oppfolgingsplan.organisasjonsnummer = ?
              AND oppfolgingsplan.uuid <> ?
            RETURNING outbox.message_type
        )
        SELECT message_type, COUNT(*) AS cancelled_count
        FROM cancelled
        GROUP BY message_type
        """.trimIndent(),
    ).use { statement ->
        var index = 0
        statement.setObject(++index, completedAt.atOffset(ZoneOffset.UTC))
        statement.setString(++index, OutboxCancellationReason.SUPERSEDED.value)
        statement.setString(
            ++index,
            OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_MIN_SIDE_ARBEIDSGIVER.value,
        )
        statement.setString(
            ++index,
            OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_DINE_SYKMELDTE.value,
        )
        statement.setString(++index, sykmeldtFnr)
        statement.setString(++index, organisasjonsnummer)
        statement.setObject(++index, supersedingOppfolgingsplanUuid)

        statement.executeQuery().use { resultSet ->
            while (resultSet.next()) {
                val messageType = OppfolgingsplanOutboxMessageType.evalueringPaaminnelseTypes.single {
                    it.value == resultSet.getString("message_type")
                }
                countByMessageType[messageType] = resultSet.getInt("cancelled_count")
            }
        }
    }
    return countByMessageType
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

sealed interface OppfolgingsplanEvalueringPaaminnelseSource {
    data class Eligible(
        val sourceData: OppfolgingsplanEvalueringPaaminnelseSourceData,
    ) : OppfolgingsplanEvalueringPaaminnelseSource

    data object NotFound : OppfolgingsplanEvalueringPaaminnelseSource

    data object NoLongerEligible : OppfolgingsplanEvalueringPaaminnelseSource
}

data class OppfolgingsplanEvalueringPaaminnelseSourceData(
    val sykmeldtFnr: String,
    val sykmeldtFullName: String,
    val organisasjonsnummer: String,
    val organisasjonsnavn: String?,
    val evalueringsdato: LocalDate,
) {
    override fun toString(): String = "OppfolgingsplanEvalueringPaaminnelseSourceData()"
}
