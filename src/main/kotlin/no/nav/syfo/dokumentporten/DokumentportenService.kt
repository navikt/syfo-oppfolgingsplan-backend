package no.nav.syfo.dokumentporten

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.exception.InternalServerErrorException
import no.nav.syfo.dokumentporten.client.Document
import no.nav.syfo.dokumentporten.client.DocumentType
import no.nav.syfo.dokumentporten.client.IDokumentportenClient
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanerForDokumentportenPublisering
import no.nav.syfo.oppfolgingsplan.db.setSendtTilDokumentportenTidspunkt
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.pdfgen.PdfGenService
import no.nav.syfo.util.logger
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

class DokumentportenService(
    private val dokumentportenClient: IDokumentportenClient,
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
        logger.info("Starting task for send documents to dokumentporten")
        val planer = withContext(Dispatchers.IO) {
            database.findOppfolgingsplanerForDokumentportenPublisering()
        }
        logger.info("Found ${planer.size} documents to send to dokumentporten")

        val failedPlans = mutableListOf<Pair<UUID, Exception>>()

        planer.forEach { oppfolgingsplan ->
            try {
                val planWithNarmestelederName = oppfolgingsplanService.getAndSetNarmestelederFullname(oppfolgingsplan)
                val pdfByteArray = pdfGenService.generatePdf(planWithNarmestelederName)
                    ?: throw InternalServerErrorException("An error occurred while generating pdf")
                dokumentportenClient.publishOppfolgingsplan(
                    oppfolgingsplan.toDocument(pdfByteArray, dateFormatter),
                )
                withContext(Dispatchers.IO) {
                    database.setSendtTilDokumentportenTidspunkt(planWithNarmestelederName.uuid, Instant.now())
                }
            } catch (ex: Exception) {
                logger.error("Failed to send oppfolgingsplan ${oppfolgingsplan.uuid} to dokumentporten", ex)
                failedPlans.add(oppfolgingsplan.uuid to ex)
            }
        }

        if (failedPlans.isNotEmpty()) {
            logger.warn("Failed to send ${failedPlans.size} of ${planer.size} oppfolgingsplaner to dokumentporten")
        } else if (planer.isNotEmpty()) {
            logger.info("Successfully sent ${planer.size} oppfolgingsplaner to dokumentporten")
        }
    }
}

fun PersistedOppfolgingsplan.toDocument(content: ByteArray, dateFormatter: DateTimeFormatter) = Document(
    documentId = this.uuid,
    orgNumber = this.organisasjonsnummer,
    content = content,
    contentType = "application/pdf",
    type = DocumentType.OPPFOLGINGSPLAN,
    title = title(),
    summary = summary(dateFormatter),
    fnr = this.sykmeldtFnr,
    fullName = this.sykmeldtFullName,
)

fun PersistedOppfolgingsplan.title(): String =
    "Oppfølgingsplan for ${this.sykmeldtFullName}"

fun PersistedOppfolgingsplan.summary(dateFormatter: DateTimeFormatter): String =
    this.narmesteLederFullName?.let {
    "${this.narmesteLederFullName} har opprettet en oppfølgingsplan for ${this.sykmeldtFullName} på \"Dine sykmeldte\" hos Nav opprettet den ${dateFormatter.format(this.createdAt)}"
} ?: "Det er opprettet en oppfølgingsplan for ${this.sykmeldtFullName} på \"Dine sykmeldte\" hos Nav opprettet den ${dateFormatter.format(this.createdAt)}"
