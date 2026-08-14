package no.nav.syfo.application.task

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.util.logger
import kotlin.time.Duration

/**
 * Runs periodically while [shouldExecute] permits it. Coordination policy is supplied explicitly
 * by the caller.
 */
abstract class RecurringTask(
    name: String,
    private val interval: Duration,
    private val shouldExecute: suspend () -> Boolean,
) {
    constructor(
        name: String,
        interval: Duration,
        leaderElection: LeaderElection,
    ) : this(name, interval, leaderElection::isLeader)

    protected val log = logger(name)
    protected val taskName: String = name

    abstract suspend fun execute()

    suspend fun runTask() = coroutineScope {
        try {
            while (isActive) {
                try {
                    if (shouldExecute()) {
                        execute()
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    log.error("Error while executing $taskName", ex)
                }
                delay(interval)
            }
        } catch (_: CancellationException) {
            log.info("$taskName stopped gracefully")
        }
    }
}
