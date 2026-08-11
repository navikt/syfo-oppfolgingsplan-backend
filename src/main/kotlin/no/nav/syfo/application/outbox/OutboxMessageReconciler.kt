package no.nav.syfo.application.outbox

import org.jetbrains.exposed.v1.jdbc.JdbcTransaction

fun interface OutboxMessageReconciler {
    fun reconcile(transaction: JdbcTransaction): Int
}
