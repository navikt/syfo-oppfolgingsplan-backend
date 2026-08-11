package no.nav.syfo.application.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.Connection

/**
 * Preserves the legacy JDBC transaction semantics during the incremental Exposed migration:
 * REPEATABLE_READ isolation and no automatic replay of failed transaction blocks.
 */
internal suspend fun <T> DatabaseInterface.exposedTransaction(
    readOnly: Boolean = false,
    block: JdbcTransaction.() -> T,
): T = withContext(Dispatchers.IO) {
    transaction(
        db = exposedDatabase,
        transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ,
        readOnly = readOnly,
    ) {
        maxAttempts = 1
        block()
    }
}

/**
 * Suspended counterpart used when an Exposed transaction must span a suspending side effect,
 * such as waiting for a broker acknowledgement while an outbox row remains locked.
 */
internal suspend fun <T> DatabaseInterface.suspendedExposedTransaction(
    readOnly: Boolean = false,
    block: suspend JdbcTransaction.() -> T,
): T = withContext(Dispatchers.IO) {
    suspendTransaction(
        db = exposedDatabase,
        transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ,
        readOnly = readOnly,
    ) {
        maxAttempts = 1
        block()
    }
}
