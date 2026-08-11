package no.nav.syfo.oppfolgingsplan.outbox

import no.nav.syfo.application.outbox.OutboxMessageReconciler
import no.nav.syfo.application.outbox.db.OutboxTable
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import no.nav.syfo.oppfolgingsplan.db.OppfolgingsplanOutboxTable
import org.jetbrains.exposed.v1.core.Cast
import org.jetbrains.exposed.v1.core.NotExists
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset

class LegacyOppfolgingsplanOutboxReconciler(
    private val clock: Clock = Clock.systemUTC(),
    private val lookback: Duration = DEFAULT_LOOKBACK,
) : OutboxMessageReconciler {
    init {
        require(!lookback.isNegative && !lookback.isZero) { "lookback must be greater than zero" }
    }

    override fun reconcile(transaction: JdbcTransaction): Int {
        val oppfolgingsplanUuidAsText = Cast<String>(
            OppfolgingsplanOutboxTable.uuid,
            TextColumnType(),
        )
        val candidates = OppfolgingsplanOutboxTable
            .select(
                OppfolgingsplanOutboxTable.eventId,
                stringLiteral(OutboxMessageType.OPPFOLGINGSPLAN_OPPRETTET.name),
                oppfolgingsplanUuidAsText,
                oppfolgingsplanUuidAsText,
                OppfolgingsplanOutboxTable.createdAt,
            ).where {
                OppfolgingsplanOutboxTable.eventId.isNotNull() and
                    OppfolgingsplanOutboxTable.varselPublishedAt.isNull() and
                    (
                        OppfolgingsplanOutboxTable.createdAt greaterEq
                            clock.instant().minus(lookback).atOffset(ZoneOffset.UTC)
                        ) and
                    NotExists(
                        OutboxTable
                            .select(OutboxTable.uuid)
                            .where { OutboxTable.uuid eq OppfolgingsplanOutboxTable.eventId },
                    )
            }

        return OutboxTable.insertIgnore(
            candidates,
            columns = listOf(
                OutboxTable.uuid,
                OutboxTable.messageType,
                OutboxTable.dedupKey,
                OutboxTable.externalRef,
                OutboxTable.scheduledAt,
            ),
        ) ?: 0
    }

    companion object {
        private val DEFAULT_LOOKBACK: Duration = Duration.ofDays(1)
    }
}
