package no.nav.syfo.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import kotlinx.coroutines.launch
import no.nav.syfo.dokumentporten.SendOppfolgingsplanTask
import no.nav.syfo.oppfolgingsplan.task.CleanupUtkastTask
import no.nav.syfo.oppfolgingsplan.task.PublishUnpublishedBudstikkaVarslerTask
import no.nav.syfo.oppfolgingsplan.task.SoftDeleteOppfolgingsplanerTask
import no.nav.syfo.oppfolgingsplan.task.SoftDeleteUnntaksvurderingerTask
import no.nav.syfo.sykmelding.kafka.SykmeldingsperiodeConsumer
import org.koin.ktor.ext.inject

fun Application.configureBackgroundTasks() {
    val sendDialogTask by inject<SendOppfolgingsplanTask>()
    val cleanupUtkastTask by inject<CleanupUtkastTask>()
    val publishUnpublishedBudstikkaVarslerTask by inject<PublishUnpublishedBudstikkaVarslerTask>()
    val softDeleteOppfolgingsplanerTask by inject<SoftDeleteOppfolgingsplanerTask>()
    val softDeleteUnntaksvurderingerTask by inject<SoftDeleteUnntaksvurderingerTask>()
    val sykmeldingsperiodeConsumer by inject<SykmeldingsperiodeConsumer>()

    val sendDialogTaskJob = launch { sendDialogTask.runTask() }
    val cleanupUtkastTaskJob = launch { cleanupUtkastTask.runTask() }
    val publishUnpublishedBudstikkaVarslerTaskJob = launch { publishUnpublishedBudstikkaVarslerTask.runTask() }
    val softDeleteOppfolgingsplanerTaskJob = launch { softDeleteOppfolgingsplanerTask.runTask() }
    val softDeleteUnntaksvurderingerTaskJob = launch { softDeleteUnntaksvurderingerTask.runTask() }
    val sykmeldingsperiodeConsumerJob = launch { sykmeldingsperiodeConsumer.runConsumer() }
    monitor.subscribe(ApplicationStopping) {
        sykmeldingsperiodeConsumer.stop()
        sendDialogTaskJob.cancel()
        cleanupUtkastTaskJob.cancel()
        publishUnpublishedBudstikkaVarslerTaskJob.cancel()
        softDeleteOppfolgingsplanerTaskJob.cancel()
        softDeleteUnntaksvurderingerTaskJob.cancel()
        sykmeldingsperiodeConsumerJob.cancel()
    }
}
