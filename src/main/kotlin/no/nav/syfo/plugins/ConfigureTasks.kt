package no.nav.syfo.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import kotlinx.coroutines.launch
import no.nav.syfo.dokumentporten.SendOppfolgingsplanTask
import no.nav.syfo.oppfolgingsplan.task.CleanupUtkastTask
import org.koin.ktor.ext.inject

fun Application.configureBackgroundTasks() {
    val sendDialogTask by inject<SendOppfolgingsplanTask>()
    val cleanupUtkastTask by inject<CleanupUtkastTask>()

    val sendDialogTaskJob = launch { sendDialogTask.runTask() }
    val cleanupUtkastTaskJob = launch { cleanupUtkastTask.runTask() }
    monitor.subscribe(ApplicationStopping) {
        sendDialogTaskJob.cancel()
        cleanupUtkastTaskJob.cancel()
    }
}
