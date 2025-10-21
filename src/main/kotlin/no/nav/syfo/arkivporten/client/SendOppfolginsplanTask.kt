package no.nav.syfo.arkivporten.client

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.exception.InternalServerErrorException
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.oppfolgingsplan.db.PersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanserForArkivportenPublisering
import no.nav.syfo.oppfolgingsplan.db.setPublisertTilArkivportenTidspunkt
import no.nav.syfo.pdfgen.PdfGenService
import no.nav.syfo.util.logger

class SendOppfolginsplanTask(
    private val leaderElection: LeaderElection,
    private val arkivportenClient: IArkivportenClient,
    private val database: DatabaseInterface,
    private val pdfGenService: PdfGenService,
) {
    private val logger = logger()
    private val dateFormatter = DateTimeFormatter
        .ofPattern("dd.MM.yyyy")
        .withLocale(Locale.forLanguageTag("nb-NO"))
        .withZone(ZoneId.of("Europe/Oslo"))

    suspend fun runTask() = coroutineScope {
        try {
            while (isActive) {
                if (leaderElection.isLeader()) {
                    try {
                        logger.info("Starting task for send documents to dialogporten")
                        val planer = database.findOppfolgingsplanserForArkivportenPublisering()
                        logger.info("Found ${planer.size} documents to send to arkivporten")
                        planer.forEach { oppfolgingsplan ->
                            val pdfByteArray = pdfGenService.generatePdf(oppfolgingsplan)
                                ?: throw InternalServerErrorException("An error occurred while generating pdf")
                            arkivportenClient.publishOppfolginsplan(
                                oppfolgingsplan.toArkivportenDocument(pdfByteArray, dateFormatter),
                            )
                            database.setPublisertTilArkivportenTidspunkt(oppfolgingsplan.uuid, Instant.now())
                        }
                    } catch (ex: Exception) {
                        logger.error("Could not send dialogs to dialogporten", ex)
                    }
                }
                // Sleep for a while before checking again
//                delay(5 * 60 * 1000) // 5 minutes
                delay(5 * 1000) // 5 minutes
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
    dialogSummary = "Oppfølgingsplan opprettet den ${dateFormatter.format(this.createdAt)} " + "av ${this.narmesteLederFullName}",
)
