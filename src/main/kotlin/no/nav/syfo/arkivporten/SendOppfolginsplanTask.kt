package no.nav.syfo.arkivporten
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import no.nav.syfo.application.isProdEnv
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.util.logger

class SendOppfolginsplanTask(
    private val leaderElection: LeaderElection,
    private val arkivportenService: ArkivportenService
    ) {
    private val logger = logger()
    suspend fun runTask() = coroutineScope {
        try {
            while (isActive) {
                if (leaderElection.isLeader() && !isProdEnv()) {
                    arkivportenService.findAndSendOppfolgingsplaner()
                }
                // Sleep for a while before checking again
                delay(5 * 60 * 1000) // 5 minutes
            }
        } catch (ex: CancellationException) {
            logger.info("cancelled delete data job", ex)
        }
    }
}
