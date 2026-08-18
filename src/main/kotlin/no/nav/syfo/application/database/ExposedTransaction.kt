package no.nav.syfo.application.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.Connection

/**
 * Preserves the legacy JDBC transaction semantics during the incremental Exposed migration:
 * REPEATABLE_READ isolation and, by default, no automatic replay. Set [maxAttempts] above one only
 * for pure database blocks where replaying the entire domain transaction is safe.
 */
internal suspend fun <T> DatabaseInterface.exposedTransaction(
    readOnly: Boolean = false,
    maxAttempts: Int = 1,
    block: JdbcTransaction.() -> T,
): T = withContext(Dispatchers.IO) {
    require(maxAttempts >= 1) { "maxAttempts must be at least one" }
    transaction(
        db = exposedDatabase,
        transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ,
        readOnly = readOnly,
    ) {
        this.maxAttempts = maxAttempts
        block()
    }
}
