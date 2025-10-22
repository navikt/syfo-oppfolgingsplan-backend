package no.nav.syfo.arkivporten
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.arkivporten.client.Document
import no.nav.syfo.arkivporten.client.DocumentType
import no.nav.syfo.oppfolgingsplan.db.PersistedOppfolgingsplan
import no.nav.syfo.util.logger

class SendOppfolginsplanTask(
    private val leaderElection: LeaderElection,
    private val arkivportenService: ArkivportenService
    ) {
    private val logger = logger()
    suspend fun runTask() = coroutineScope {
        try {
            while (isActive) {
                if (leaderElection.isLeader()) {
                    arkivportenService.finddAndPublishOppfolgingsplaner()
                }
                // Sleep for a while before checking again
                delay(5 * 60 * 1000) // 5 minutes
            }
        } catch (ex: CancellationException) {
            logger.info("cancelled delete data job", ex)
        }
    }
}

fun PersistedOppfolgingsplan.toArkivportenDocument(content: ByteArray, dateFormatter: DateTimeFormatter) = Document(
    documentId = this.uuid,
    orgnumber = this.organisasjonsnummer,
    content = content,
    contentType = "application/pdf",
    type = DocumentType.OPPFOLGINGSPLAN,
    dialogTitle = "Oppfølgingsplan for ${this.sykmeldtFullName}",
    dialogSummary = "Oppfølgingsplan opprettet den ${dateFormatter.format(this.createdAt)} " +
        "av ${this.narmesteLederFullName}",
)
