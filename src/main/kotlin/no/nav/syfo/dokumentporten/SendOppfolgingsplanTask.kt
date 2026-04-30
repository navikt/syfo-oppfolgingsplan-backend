package no.nav.syfo.dokumentporten

import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.application.task.RecurringTask
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class SendOppfolgingsplanTask(
    leaderElection: LeaderElection,
    private val dokumentportenService: DokumentportenService,
    interval: Duration = 5.minutes,
) : RecurringTask(
    name = SendOppfolgingsplanTask::class.qualifiedName!!,
    interval = interval,
    leaderElection = leaderElection,
) {
    override suspend fun execute() {
        dokumentportenService.findAndSendOppfolgingsplaner()
    }
}
