package no.nav.syfo.oppfolgingsplan.task

import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.application.task.RecurringTask
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class PublishUnpublishedBudstikkaVarslerTask(
    leaderElection: LeaderElection,
    private val oppfolgingsplanService: OppfolgingsplanService,
    interval: Duration = 1.minutes,
) : RecurringTask(
    name = PublishUnpublishedBudstikkaVarslerTask::class.qualifiedName!!,
    interval = interval,
    leaderElection = leaderElection,
) {
    override suspend fun execute() {
        val publishedCount = oppfolgingsplanService.retryUnpublishedBudstikkaVarsler()
        if (publishedCount > 0) {
            log.info("Published $publishedCount previously unpublished Budstikka varsler")
        } else {
            log.debug("No unpublished Budstikka varsler found")
        }
    }
}
