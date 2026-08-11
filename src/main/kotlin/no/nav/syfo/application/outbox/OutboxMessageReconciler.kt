package no.nav.syfo.application.outbox

import java.sql.Connection

fun interface OutboxMessageReconciler {
    fun reconcile(connection: Connection): Int
}
