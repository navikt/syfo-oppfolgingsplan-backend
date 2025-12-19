package no.nav.syfo.dokumentporten
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import no.nav.syfo.application.isProdEnv
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.util.logger

class SendOppfolgingsplanTask(
    private val leaderElection: LeaderElection,
    private val dokumentportenService: DokumentportenService
    ) {
    private val logger = logger()
    suspend fun runTask() = coroutineScope {
        try {
            while (isActive) {
                if (leaderElection.isLeader()) {
                    dokumentportenService.findAndSendOppfolgingsplaner()
                }
                // Sleep for a while before checking again
                delay(5 * 60 * 1000) // 5 minutes
            }
        } catch (ex: CancellationException) {
            logger.info("cancelled delete data job", ex)
        }
    }
}
