package no.nav.syfo.application.database

import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.Connection

internal fun <T> DatabaseInterface.exposedTransaction(
    readOnly: Boolean = false,
    block: JdbcTransaction.() -> T,
): T = transaction(
    db = exposedDatabase,
    transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ,
    readOnly = readOnly,
) {
    maxAttempts = 1
    block()
}
