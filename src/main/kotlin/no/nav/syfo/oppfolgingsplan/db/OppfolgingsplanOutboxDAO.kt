package no.nav.syfo.oppfolgingsplan.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.exposedTransaction
import no.nav.syfo.application.outbox.db.OutboxTable
import no.nav.syfo.application.outbox.domain.OutboxCancellationReason
import no.nav.syfo.oppfolgingsplan.outbox.OppfolgingsplanOutboxMessageType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
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

suspend fun DatabaseInterface.findOppfolgingsplanEvalueringPaaminnelseSource(
    oppfolgingsplanUuid: UUID,
    clock: Clock = Clock.systemUTC(),
): OppfolgingsplanEvalueringPaaminnelseSource = exposedTransaction(readOnly = true) {
    val today = LocalDate.now(clock.withZone(ZONE_OSLO))
    val sourceRow = OppfolgingsplanTable
        .select(
            OppfolgingsplanTable.sykmeldtFnr,
            OppfolgingsplanTable.sykmeldtFullName,
            OppfolgingsplanTable.organisasjonsnummer,
            OppfolgingsplanTable.organisasjonsnavn,
            OppfolgingsplanTable.evalueringsdato,
        ).where {
            OppfolgingsplanTable.uuid eq oppfolgingsplanUuid
        }.singleOrNull()
        ?: return@exposedTransaction OppfolgingsplanEvalueringPaaminnelseSource.NotFound

    val sykmeldtFnr = sourceRow[OppfolgingsplanTable.sykmeldtFnr]
    val organisasjonsnummer = sourceRow[OppfolgingsplanTable.organisasjonsnummer]
    val hasActiveSykmeldingsperiode = SykmeldingsperiodeTable
        .select(SykmeldingsperiodeTable.id)
        .where {
            (SykmeldingsperiodeTable.sykmeldtFnr eq sykmeldtFnr) and
                (SykmeldingsperiodeTable.organisasjonsnummer eq organisasjonsnummer) and
                SykmeldingsperiodeTable.invalidatedAt.isNull() and
                (SykmeldingsperiodeTable.fom lessEq today) and
                (SykmeldingsperiodeTable.tom greaterEq today)
        }.limit(1)
        .any()

    if (!hasActiveSykmeldingsperiode) {
        return@exposedTransaction OppfolgingsplanEvalueringPaaminnelseSource.NoLongerEligible
    }

    OppfolgingsplanEvalueringPaaminnelseSource.Eligible(
        OppfolgingsplanEvalueringPaaminnelseSourceData(
            sykmeldtFnr = sykmeldtFnr,
            sykmeldtFullName = sourceRow[OppfolgingsplanTable.sykmeldtFullName],
            organisasjonsnummer = organisasjonsnummer,
            organisasjonsnavn = sourceRow[OppfolgingsplanTable.organisasjonsnavn],
            evalueringsdato = sourceRow[OppfolgingsplanTable.evalueringsdato],
        ),
    )
}

fun JdbcTransaction.cancelReadySupersededEvalueringPaaminnelseRows(
    sykmeldtFnr: String,
    organisasjonsnummer: String,
    supersedingOppfolgingsplanUuid: UUID,
    completedAt: Instant,
): Map<OppfolgingsplanOutboxMessageType, Int> {
    val countByMessageType = OppfolgingsplanOutboxMessageType.evalueringPaaminnelseTypes
        .associateWith { 0 }
        .toMutableMap()

    return exec(
        stmt = """
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
        args = listOf(
            OutboxTable.completedAt.columnType to completedAt.atOffset(ZoneOffset.UTC),
            OutboxTable.cancellationReason.columnType to OutboxCancellationReason.SUPERSEDED.value,
            OutboxTable.messageType.columnType to
                OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_MIN_SIDE_ARBEIDSGIVER.value,
            OutboxTable.messageType.columnType to
                OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_DINE_SYKMELDTE.value,
            OppfolgingsplanTable.sykmeldtFnr.columnType to sykmeldtFnr,
            OppfolgingsplanTable.organisasjonsnummer.columnType to organisasjonsnummer,
            OppfolgingsplanTable.uuid.columnType to supersedingOppfolgingsplanUuid,
        ),
        explicitStatementType = StatementType.SELECT,
    ) { resultSet ->
        while (resultSet.next()) {
            val messageType = OppfolgingsplanOutboxMessageType.evalueringPaaminnelseTypes.single {
                it.value == resultSet.getString("message_type")
            }
            countByMessageType[messageType] = resultSet.getInt("cancelled_count")
        }
        countByMessageType
    }
        ?: error("Cancellation query returned no result set")
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
