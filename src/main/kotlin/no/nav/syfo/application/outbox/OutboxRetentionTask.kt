package no.nav.syfo.application.outbox

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.exposedTransaction
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.application.outbox.db.deleteTerminalOutboxMessages
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import no.nav.syfo.application.task.RecurringTask
import java.time.Clock
import java.time.Duration
import kotlin.time.Duration.Companion.days

data class OutboxRetentionPolicy(
    val messageType: OutboxMessageType,
    val retention: Duration,
) {
    init {
        require(!retention.isNegative && !retention.isZero) { "retention must be greater than zero" }
    }
}

class OutboxRetentionTask(
    private val database: DatabaseInterface,
    leaderElection: LeaderElection,
    policies: List<OutboxRetentionPolicy>,
    private val clock: Clock = Clock.systemUTC(),
    private val batchSize: Int = 500,
    private val maxBatchIterations: Int = 1000,
    interval: kotlin.time.Duration = 1.days,
) : RecurringTask(
    name = requireNotNull(OutboxRetentionTask::class.qualifiedName),
    interval = interval,
    leaderElection = leaderElection,
) {
    private val policiesByType = policies.associateBy { it.messageType.value }

    init {
        require(policies.isNotEmpty()) { "At least one outbox retention policy must be configured" }
        require(policies.all { it.messageType.value.isNotBlank() }) { "Outbox message type values must not be blank" }
        require(policies.size == policiesByType.size) { "Only one retention policy can be configured per message type" }
        require(batchSize > 0) { "batchSize must be greater than zero" }
        require(maxBatchIterations > 0) { "maxBatchIterations must be greater than zero" }
    }

    override suspend fun execute() {
        policiesByType.values.forEach { policy -> deleteExpired(policy) }
    }

    private suspend fun deleteExpired(policy: OutboxRetentionPolicy) {
        val completedBefore = clock.instant().minus(policy.retention)
        var totalDeleted = 0
        repeat(maxBatchIterations) {
            val deleted = database.exposedTransaction {
                deleteTerminalOutboxMessages(
                    messageType = policy.messageType,
                    completedBefore = completedBefore,
                    batchSize = batchSize,
                )
            }
            totalDeleted += deleted
            if (deleted < batchSize) {
                if (totalDeleted > 0) {
                    log.info("Deleted {} terminal outbox messages for messageType={}", totalDeleted, policy.messageType.value)
                }
                return
            }
        }
        log.warn(
            "Stopped outbox retention after {} batches for messageType={}; deletedMessages={}",
            maxBatchIterations,
            policy.messageType.value,
            totalDeleted,
        )
    }
}
