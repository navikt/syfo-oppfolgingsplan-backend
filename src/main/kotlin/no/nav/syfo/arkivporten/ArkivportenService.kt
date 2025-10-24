package no.nav.syfo.arkivporten

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.exception.InternalServerErrorException
import no.nav.syfo.arkivporten.client.Document
import no.nav.syfo.arkivporten.client.DocumentType
import no.nav.syfo.arkivporten.client.IArkivportenClient
import no.nav.syfo.oppfolgingsplan.db.PersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanserForArkivportenPublisering
import no.nav.syfo.oppfolgingsplan.db.setSendtTilArkivportenTidspunkt
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.pdfgen.PdfGenService
import no.nav.syfo.util.logger

class ArkivportenService(
    private val arkivportenClient: IArkivportenClient,
    private val database: DatabaseInterface,
    private val pdfGenService: PdfGenService,
    private val oppfolgingsplanService: OppfolgingsplanService,
) {
    private val dateFormatter = DateTimeFormatter
        .ofPattern("dd.MM.yyyy")
        .withLocale(Locale.forLanguageTag("nb-NO"))
        .withZone(ZoneId.of("Europe/Oslo"))

    private val logger = logger()
    suspend fun findAndSendOppfolgingsplaner() {
        try {
            logger.info("Starting task for send documents to arkivporten")
            val planer = database.findOppfolgingsplanserForArkivportenPublisering()
            logger.info("Found ${planer.size} documents to send to arkivporten")
            planer.forEach { oppfolgingsplan ->
                val planWithNarmestelederName = oppfolgingsplanService.getAndSetNarmestelederFullname(oppfolgingsplan)
                val pdfByteArray = pdfGenService.generatePdf(planWithNarmestelederName)
                    ?: throw InternalServerErrorException("An error occurred while generating pdf")
                arkivportenClient.publishOppfolginsplan(
                    oppfolgingsplan.toArkivportenDocument(pdfByteArray, dateFormatter),
                )
                database.setSendtTilArkivportenTidspunkt(planWithNarmestelederName.uuid, Instant.now())
            }
        } catch (ex: Exception) {
            logger.error("Could not send document to arkivporten", ex)
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
    dialogSummary = getDialogSummary(dateFormatter),
)

fun PersistedOppfolgingsplan.getDialogSummary(dateFormatter: DateTimeFormatter): String =
    narmesteLederFullName?.let {
        "Oppfølgingsplan opprettet den ${dateFormatter.format(this.createdAt)} " +
            "av ${this.narmesteLederFullName}"
    } ?: "Oppfølgingsplan opprettet den ${dateFormatter.format(this.createdAt)}"
