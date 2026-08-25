package no.nav.syfo.oppfolgingsplan.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.exposedTransaction
import no.nav.syfo.application.outbox.db.OutboxTable
import no.nav.syfo.application.outbox.db.enqueueOutboxMessage
import no.nav.syfo.application.outbox.domain.NewOutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxCancellationReason
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.dinesykmeldte.client.getOrganizationName
import no.nav.syfo.oppfolgingsplan.dto.CreateOppfolgingsplanRequest
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.toJsonString
import no.nav.syfo.oppfolgingsplan.outbox.OppfolgingsplanEvalueringPaaminnelseOutboxMetrics
import no.nav.syfo.oppfolgingsplan.outbox.OppfolgingsplanOutboxMessageType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteReturning
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.json.jsonb
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

private val ZONE_OSLO: ZoneId = ZoneId.of("Europe/Oslo")
private const val EVALUERING_PAAMINNELSE_DAYS_BEFORE = 3L
private const val EVALUERING_PAAMINNELSE_HOUR = 9

class OppfolgingsplanEvalueringPaaminnelseRepository(
    private val database: DatabaseInterface,
) {
    suspend fun persistOppfolgingsplanAndDeleteUtkast(
        narmesteLederFnr: String,
        sykmeldt: Sykmeldt,
        createOppfolgingsplanRequest: CreateOppfolgingsplanRequest,
        stillingstittel: String?,
        stillingsprosent: BigDecimal?,
    ): UUID {
        val persistResult = database.exposedTransaction(maxAttempts = 3) {
            val utkastCreatedAt = EvalueringPaaminnelseOppfolgingsplanUtkastTable
                .deleteReturning(returning = listOf(EvalueringPaaminnelseOppfolgingsplanUtkastTable.createdAt)) {
                    EvalueringPaaminnelseOppfolgingsplanUtkastTable.narmesteLederId eq sykmeldt.narmestelederId
                }.singleOrNull()
                ?.get(EvalueringPaaminnelseOppfolgingsplanUtkastTable.createdAt)
            val eventId = UUID.randomUUID()

            val insertedOppfolgingsplanRow = EvalueringPaaminnelseOppfolgingsplanTable.insertReturning(
                returning = listOf(
                    EvalueringPaaminnelseOppfolgingsplanTable.uuid,
                    EvalueringPaaminnelseOppfolgingsplanTable.createdAt,
                ),
            ) {
                it[EvalueringPaaminnelseOppfolgingsplanTable.sykmeldtFnr] = sykmeldt.fnr
                it[EvalueringPaaminnelseOppfolgingsplanTable.sykmeldtFullName] = sykmeldt.navn
                it[EvalueringPaaminnelseOppfolgingsplanTable.narmesteLederId] = sykmeldt.narmestelederId
                it[EvalueringPaaminnelseOppfolgingsplanTable.narmesteLederFnr] = narmesteLederFnr
                it[EvalueringPaaminnelseOppfolgingsplanTable.organisasjonsnummer] = sykmeldt.orgnummer
                it[EvalueringPaaminnelseOppfolgingsplanTable.organisasjonsnavn] = sykmeldt.getOrganizationName()
                it[EvalueringPaaminnelseOppfolgingsplanTable.stillingstittel] = stillingstittel
                it[EvalueringPaaminnelseOppfolgingsplanTable.stillingsprosent] = stillingsprosent
                it[EvalueringPaaminnelseOppfolgingsplanTable.content] = createOppfolgingsplanRequest.content.toJsonString()
                it[EvalueringPaaminnelseOppfolgingsplanTable.evalueringsdato] = createOppfolgingsplanRequest.evalueringsdato
                it[EvalueringPaaminnelseOppfolgingsplanTable.evalueringPaaminnelse] =
                    createOppfolgingsplanRequest.evalueringPaaminnelse
                it[EvalueringPaaminnelseOppfolgingsplanTable.evalueringPaaminnelseOutboxAt] = null
                it[EvalueringPaaminnelseOppfolgingsplanTable.skalDelesMedLege] = false
                it[EvalueringPaaminnelseOppfolgingsplanTable.skalDelesMedVeileder] = false
                it[EvalueringPaaminnelseOppfolgingsplanTable.utkastCreatedAt] = utkastCreatedAt
                it[EvalueringPaaminnelseOppfolgingsplanTable.createdAt] = CurrentTimestampWithTimeZone
                it[EvalueringPaaminnelseOppfolgingsplanTable.eventId] = eventId
            }.single()

            val oppfolgingsplanUuid = insertedOppfolgingsplanRow[EvalueringPaaminnelseOppfolgingsplanTable.uuid]
            val createdAt = insertedOppfolgingsplanRow[EvalueringPaaminnelseOppfolgingsplanTable.createdAt].toInstant()

            val supersededReminderCountByChannel = cancelReadySupersededEvalueringPaaminnelseRows(
                sykmeldtFnr = sykmeldt.fnr,
                organisasjonsnummer = sykmeldt.orgnummer,
                supersedingOppfolgingsplanUuid = oppfolgingsplanUuid,
                completedAt = createdAt,
            )

            check(
                enqueueOutboxMessage(
                    NewOutboxMessage(
                        uuid = eventId,
                        messageType = OppfolgingsplanOutboxMessageType.CREATED,
                        dedupKey = oppfolgingsplanUuid.toString(),
                        externalRef = oppfolgingsplanUuid.toString(),
                        payload = "{}",
                        availableAt = createdAt,
                    ),
                ),
            ) { "A new oppfolgingsplan must create a new outbox command" }

            val createdReminderCountByChannel = OppfolgingsplanOutboxMessageType.evalueringPaaminnelseTypes
                .associateWith { 0 }
                .toMutableMap()
            if (createOppfolgingsplanRequest.evalueringPaaminnelse) {
                OppfolgingsplanOutboxMessageType.evalueringPaaminnelseTypes.forEach { channelMessageType ->
                    check(
                        enqueueOutboxMessage(
                            NewOutboxMessage(
                                messageType = channelMessageType,
                                dedupKey = oppfolgingsplanUuid.toString(),
                                externalRef = oppfolgingsplanUuid.toString(),
                                payload = "{}",
                                availableAt = createOppfolgingsplanRequest.evalueringsdato
                                    .toEvalueringPaaminnelseAvailableAt(),
                            ),
                        ),
                    ) {
                        "A new oppfolgingsplan with evalueringPaaminnelse must create one outbox command per channel"
                    }
                    createdReminderCountByChannel[channelMessageType] = 1
                }
            }

            PersistOppfolgingsplanResult(
                oppfolgingsplanUuid = oppfolgingsplanUuid,
                createdReminderCountByChannel = createdReminderCountByChannel,
                supersededReminderCountByChannel = supersededReminderCountByChannel,
            )
        }

        OppfolgingsplanEvalueringPaaminnelseOutboxMetrics.incrementCreated(
            persistResult.createdReminderCountByChannel,
        )
        OppfolgingsplanEvalueringPaaminnelseOutboxMetrics.incrementSuperseded(
            persistResult.supersededReminderCountByChannel,
        )
        return persistResult.oppfolgingsplanUuid
    }

    suspend fun findOppfolgingsplanEvalueringPaaminnelseSource(
        oppfolgingsplanUuid: UUID,
        clock: Clock = Clock.systemUTC(),
    ): OppfolgingsplanEvalueringPaaminnelseSource = database.exposedTransaction(readOnly = true) {
        val today = LocalDate.now(clock.withZone(ZONE_OSLO))
        val sourceRow = EvalueringPaaminnelseOppfolgingsplanTable
            .select(
                EvalueringPaaminnelseOppfolgingsplanTable.sykmeldtFnr,
                EvalueringPaaminnelseOppfolgingsplanTable.sykmeldtFullName,
                EvalueringPaaminnelseOppfolgingsplanTable.organisasjonsnummer,
                EvalueringPaaminnelseOppfolgingsplanTable.organisasjonsnavn,
                EvalueringPaaminnelseOppfolgingsplanTable.evalueringsdato,
            ).where {
                EvalueringPaaminnelseOppfolgingsplanTable.uuid eq oppfolgingsplanUuid
            }.singleOrNull()
            ?: return@exposedTransaction OppfolgingsplanEvalueringPaaminnelseSource.NotFound

        val sykmeldtFnr = sourceRow[EvalueringPaaminnelseOppfolgingsplanTable.sykmeldtFnr]
        val organisasjonsnummer = sourceRow[EvalueringPaaminnelseOppfolgingsplanTable.organisasjonsnummer]
        val hasActiveSykmeldingsperiode = EvalueringPaaminnelseSykmeldingsperiodeTable
            .select(EvalueringPaaminnelseSykmeldingsperiodeTable.id)
            .where {
                (EvalueringPaaminnelseSykmeldingsperiodeTable.sykmeldtFnr eq sykmeldtFnr) and
                    (EvalueringPaaminnelseSykmeldingsperiodeTable.organisasjonsnummer eq organisasjonsnummer) and
                    EvalueringPaaminnelseSykmeldingsperiodeTable.invalidatedAt.isNull() and
                    (EvalueringPaaminnelseSykmeldingsperiodeTable.fom lessEq today) and
                    (EvalueringPaaminnelseSykmeldingsperiodeTable.tom greaterEq today)
            }.limit(1)
            .any()

        if (!hasActiveSykmeldingsperiode) {
            return@exposedTransaction OppfolgingsplanEvalueringPaaminnelseSource.NoLongerEligible
        }

        OppfolgingsplanEvalueringPaaminnelseSource.Eligible(
            OppfolgingsplanEvalueringPaaminnelseSourceData(
                sykmeldtFnr = sykmeldtFnr,
                sykmeldtFullName = sourceRow[EvalueringPaaminnelseOppfolgingsplanTable.sykmeldtFullName],
                organisasjonsnummer = organisasjonsnummer,
                organisasjonsnavn = sourceRow[EvalueringPaaminnelseOppfolgingsplanTable.organisasjonsnavn],
                evalueringsdato = sourceRow[EvalueringPaaminnelseOppfolgingsplanTable.evalueringsdato],
            ),
        )
    }

    private data class PersistOppfolgingsplanResult(
        val oppfolgingsplanUuid: UUID,
        val createdReminderCountByChannel: Map<OppfolgingsplanOutboxMessageType, Int>,
        val supersededReminderCountByChannel: Map<OppfolgingsplanOutboxMessageType, Int>,
    )

    private fun LocalDate.toEvalueringPaaminnelseAvailableAt(): Instant = minusDays(EVALUERING_PAAMINNELSE_DAYS_BEFORE)
        .atTime(EVALUERING_PAAMINNELSE_HOUR, 0)
        .atZone(ZONE_OSLO)
        .toInstant()

    private fun JdbcTransaction.cancelReadySupersededEvalueringPaaminnelseRows(
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
                EvalueringPaaminnelseOppfolgingsplanTable.sykmeldtFnr.columnType to sykmeldtFnr,
                EvalueringPaaminnelseOppfolgingsplanTable.organisasjonsnummer.columnType to organisasjonsnummer,
                EvalueringPaaminnelseOppfolgingsplanTable.uuid.columnType to supersedingOppfolgingsplanUuid,
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

private object EvalueringPaaminnelseOppfolgingsplanTable : Table("oppfolgingsplan") {
    val uuid = javaUUID("uuid").databaseGenerated()
    val sykmeldtFnr = text("sykmeldt_fnr")
    val sykmeldtFullName = text("sykmeldt_full_name")
    val narmesteLederId = text("narmeste_leder_id")
    val narmesteLederFnr = text("narmeste_leder_fnr")
    val organisasjonsnummer = text("organisasjonsnummer")
    val organisasjonsnavn = text("organisasjonsnavn").nullable()
    val stillingstittel = text("stillingstittel").nullable()
    val stillingsprosent = decimal("stillingsprosent", 5, 2).nullable()
    val content = jsonb<String>("content", { it }, { it })
    val evalueringsdato = date("evalueringsdato")
    val evalueringPaaminnelse = bool("evaluering_paaminnelse")
    val evalueringPaaminnelseOutboxAt = timestampWithTimeZone("evaluering_paaminnelse_outbox_at").nullable()
    val skalDelesMedLege = bool("skal_deles_med_lege")
    val skalDelesMedVeileder = bool("skal_deles_med_veileder")
    val utkastCreatedAt = timestampWithTimeZone("utkast_created_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val eventId = javaUUID("event_id").nullable()

    override val primaryKey = PrimaryKey(uuid)
}

private object EvalueringPaaminnelseOppfolgingsplanUtkastTable : Table("oppfolgingsplan_utkast") {
    val uuid = javaUUID("uuid").databaseGenerated()
    val narmesteLederId = text("narmeste_leder_id")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(uuid)
}

private object EvalueringPaaminnelseSykmeldingsperiodeTable : Table("sykmeldingsperiode") {
    val id = javaUUID("id").databaseGenerated()
    val sykmeldtFnr = text("sykmeldt_fnr")
    val organisasjonsnummer = text("organisasjonsnummer")
    val fom = date("fom")
    val tom = date("tom")
    val invalidatedAt = timestampWithTimeZone("invalidated_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
