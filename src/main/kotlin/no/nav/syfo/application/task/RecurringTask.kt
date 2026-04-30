package no.nav.syfo.application.task

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.util.logger
import kotlin.time.Duration

abstract class RecurringTask(
    name: String,
    private val interval: Duration,
    private val leaderElection: LeaderElection,
) {
    protected val log = logger(name)
    protected val taskName: String = name

    abstract suspend fun execute()

    suspend fun runTask() = coroutineScope {
        try {
            while (isActive) {
                try {
                    if (leaderElection.isLeader()) {
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
