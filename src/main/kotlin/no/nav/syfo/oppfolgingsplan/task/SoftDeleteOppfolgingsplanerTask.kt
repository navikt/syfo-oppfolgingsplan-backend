package no.nav.syfo.oppfolgingsplan.task

import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.application.task.RecurringTask
import no.nav.syfo.oppfolgingsplan.api.v1.COUNT_OPPFOLGINGSPLAN_SOFT_DELETED
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class SoftDeleteOppfolgingsplanerTask(
    leaderElection: LeaderElection,
    private val oppfolgingsplanService: OppfolgingsplanService,
    interval: Duration = 1.days,
) : RecurringTask(
    name = SoftDeleteOppfolgingsplanerTask::class.qualifiedName!!,
    interval = interval,
    leaderElection = leaderElection,
) {
    override suspend fun execute() {
        log.info("Starting task for soft-delete expired oppfolgingsplaner")
        val softDeletedOppfolgingsplaner = oppfolgingsplanService.softDeleteExpiredOppfolgingsplaner()

        if (softDeletedOppfolgingsplaner > 0) {
            COUNT_OPPFOLGINGSPLAN_SOFT_DELETED.increment(softDeletedOppfolgingsplaner.toDouble())
            log.info("Soft-deleted $softDeletedOppfolgingsplaner expired oppfolgingsplaner")
        } else {
            log.info("Found 0 expired oppfolgingsplaner to soft-delete")
        }
    }

    companion object {
        internal fun intervalForEnvironment(isProdEnv: Boolean): Duration = if (isProdEnv) {
            1.days
        } else {
            5.minutes
        }
    }
}
