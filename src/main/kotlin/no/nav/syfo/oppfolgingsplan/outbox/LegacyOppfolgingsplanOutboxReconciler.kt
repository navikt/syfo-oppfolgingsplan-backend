package no.nav.syfo.oppfolgingsplan.outbox

import no.nav.syfo.application.outbox.OutboxMessageReconciler
import java.sql.Connection

class LegacyOppfolgingsplanOutboxReconciler : OutboxMessageReconciler {
    override fun reconcile(connection: Connection): Int = connection.prepareStatement(
        """
        INSERT INTO outbox (uuid, message_type, dedup_key, external_ref, scheduled_at)
        SELECT event_id,
               'OPPFOLGINGSPLAN_OPPRETTET',
               uuid::TEXT,
               uuid::TEXT,
               created_at
        FROM oppfolgingsplan
        WHERE event_id IS NOT NULL
          AND varsel_published_at IS NULL
        ON CONFLICT DO NOTHING
        """.trimIndent(),
    ).use {
        it.executeUpdate()
    }
}
