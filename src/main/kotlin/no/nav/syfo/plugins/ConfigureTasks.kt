package no.nav.syfo.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import kotlinx.coroutines.launch
import no.nav.syfo.arkivporten.SendOppfolgingsplanTask
import org.koin.ktor.ext.inject

fun Application.configureBackgroundTasks() {
    val sendDialogTask by inject<SendOppfolgingsplanTask>()

    val sendDialogTaskJob = launch { sendDialogTask.runTask() }
    monitor.subscribe(ApplicationStopping) {
        sendDialogTaskJob.cancel()
    }
}
