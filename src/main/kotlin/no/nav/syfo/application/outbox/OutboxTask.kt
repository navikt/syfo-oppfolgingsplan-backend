package no.nav.syfo.application.outbox

import no.nav.syfo.application.task.RecurringTask
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Generic scheduler. It is registered only when at least one domain adapter is introduced. */
class OutboxTask(
    private val processor: OutboxProcessor,
    interval: Duration = 1.minutes,
) : RecurringTask(
    name = requireNotNull(OutboxTask::class.qualifiedName),
    interval = interval,
    shouldExecute = { true },
) {
    override suspend fun execute() {
        val result = processor.processReadyMessages()
        if (result.processed == 0) {
            log.debug("No ready outbox messages found")
            return
        }

        log.info(
            "Processed {} outbox messages: sent={}, cancelled={}, deferred={}, retryScheduled={}",
            result.processed,
            result.sent,
            result.cancelled,
            result.deferred,
            result.retryScheduled,
        )
    }
}
