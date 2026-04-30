package no.nav.syfo.oppfolgingsplan.task

import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.application.task.RecurringTask
import no.nav.syfo.oppfolgingsplan.api.v1.COUNT_OPPFOLGINGSPLAN_DRAFT_AUTO_DELETED
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

class CleanupUtkastTask(
    leaderElection: LeaderElection,
    private val oppfolgingsplanService: OppfolgingsplanService,
    interval: Duration = 1.days,
) : RecurringTask(
    name = CleanupUtkastTask::class.qualifiedName!!,
    interval = interval,
    leaderElection = leaderElection,
) {
    override suspend fun execute() {
        val deletedDrafts = oppfolgingsplanService.deleteExpiredOppfolgingsplanUtkast()

        if (deletedDrafts > 0) {
            COUNT_OPPFOLGINGSPLAN_DRAFT_AUTO_DELETED.increment(deletedDrafts.toDouble())
            log.info("Deleted $deletedDrafts expired oppfolgingsplan drafts")
        } else {
            log.debug("No expired oppfolgingsplan drafts to delete")
        }
    }
}
