package no.nav.syfo.oppfolgingsplan.task

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.oppfolgingsplan.api.v1.COUNT_OPPFOLGINGSPLAN_DRAFT_AUTO_DELETED
import no.nav.syfo.oppfolgingsplan.service.DEFAULT_UTKAST_CLEANUP_BATCH_SIZE
import no.nav.syfo.oppfolgingsplan.service.OPPFOLGINGSPLAN_UTKAST_RETENTION_MONTHS
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.util.logger

private const val ONE_DAY_IN_MILLIS = 24 * 60 * 60 * 1000L

class CleanupUtkastTask(
    private val leaderElection: LeaderElection,
    private val oppfolgingsplanService: OppfolgingsplanService,
    private val delayMillis: Long = ONE_DAY_IN_MILLIS,
) {
    private val logger = logger()

    internal suspend fun runCleanup(): Int = try {
        if (!leaderElection.isLeader()) {
            return 0
        }

        val deletedDrafts = oppfolgingsplanService.deleteExpiredOppfolgingsplanUtkast(
            retentionMonths = OPPFOLGINGSPLAN_UTKAST_RETENTION_MONTHS,
            batchSize = DEFAULT_UTKAST_CLEANUP_BATCH_SIZE,
        )

        if (deletedDrafts > 0) {
            COUNT_OPPFOLGINGSPLAN_DRAFT_AUTO_DELETED.increment(deletedDrafts.toDouble())
            logger.info("Deleted $deletedDrafts expired oppfolgingsplan drafts")
        }

        deletedDrafts
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        logger.error("Failed to cleanup expired oppfolgingsplan drafts", exception)
        0
    }

    suspend fun runTask() = coroutineScope {
        try {
            while (isActive) {
                runCleanup()
                delay(delayMillis)
            }
        } catch (exception: CancellationException) {
            logger.info("Cancelled cleanup utkast task", exception)
        }
    }
}
