package no.nav.syfo.outbox

import no.nav.syfo.outbox.domain.OutboxMessage
import no.nav.syfo.outbox.domain.OutboxMessageType
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction

interface OutboxMessageHandler {
    fun getMessageType(): OutboxMessageType
    fun shouldDefer(message: OutboxMessage): Boolean = false
    fun send(): OutboxResult
    fun evaluate(transaction: JdbcTransaction, message: OutboxMessage): OutboxEvaluationResult
}

enum class OutboxResult {
    SENT,
    FAILED,
}

sealed class OutboxEvaluationResult {
    data object Success : OutboxEvaluationResult()
    data class Failure(val failedRules: List<EvaluationRule>) : OutboxEvaluationResult()
}

