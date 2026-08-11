package no.nav.syfo.oppfolgingsplan.task

import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.application.task.RecurringTask
import no.nav.syfo.oppfolgingsplan.api.v1.COUNT_UNNTAKSVURDERING_SOFT_DELETED
import no.nav.syfo.oppfolgingsplan.service.UnntaksvurderingService
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class SoftDeleteUnntaksvurderingerTask(
    leaderElection: LeaderElection,
    private val unntaksvurderingService: UnntaksvurderingService,
    interval: Duration = 1.days,
) : RecurringTask(
    name = SoftDeleteUnntaksvurderingerTask::class.qualifiedName!!,
    interval = interval,
    leaderElection = leaderElection,
) {
    override suspend fun execute() {
        log.info("Starting task for soft-delete expired unntaksvurderinger")
        val softDeletedUnntaksvurderinger = unntaksvurderingService.softDeleteExpiredUnntaksvurderinger()

        if (softDeletedUnntaksvurderinger > 0) {
            COUNT_UNNTAKSVURDERING_SOFT_DELETED.increment(softDeletedUnntaksvurderinger.toDouble())
            log.info("Soft-deleted $softDeletedUnntaksvurderinger expired unntaksvurderinger")
        } else {
            log.info("Found 0 expired unntaksvurderinger to soft-delete")
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
