package no.nav.syfo.arkivporten

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.exception.InternalServerErrorException
import no.nav.syfo.arkivporten.client.Document
import no.nav.syfo.arkivporten.client.DocumentType
import no.nav.syfo.arkivporten.client.IArkivportenClient
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanserForArkivportenPublisering
import no.nav.syfo.oppfolgingsplan.db.setSendtTilArkivportenTidspunkt
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.pdfgen.PdfGenService
import no.nav.syfo.util.logger
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

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
        logger.info("Starting task for send documents to arkivporten")
        val planer = database.findOppfolgingsplanserForArkivportenPublisering()
        logger.info("Found ${planer.size} documents to send to arkivporten")

        val failedPlans = mutableListOf<Pair<UUID, Exception>>()

        planer.forEach { oppfolgingsplan ->
            try {
                val planWithNarmestelederName = oppfolgingsplanService.getAndSetNarmestelederFullname(oppfolgingsplan)
                val pdfByteArray = pdfGenService.generatePdf(planWithNarmestelederName)
                    ?: throw InternalServerErrorException("An error occurred while generating pdf")
                arkivportenClient.publishOppfolgingsplan(
                    oppfolgingsplan.toArkivportenDocument(pdfByteArray, dateFormatter),
                )
                database.setSendtTilArkivportenTidspunkt(planWithNarmestelederName.uuid, Instant.now())
            } catch (ex: Exception) {
                logger.error("Failed to send oppfolgingsplan ${oppfolgingsplan.uuid} to arkivporten", ex)
                failedPlans.add(oppfolgingsplan.uuid to ex)
            }
        }

        if (failedPlans.isNotEmpty()) {
            logger.warn("Failed to send ${failedPlans.size} of ${planer.size} oppfolgingsplaner to arkivporten")
        } else if (planer.isNotEmpty()) {
            logger.info("Successfully sent ${planer.size} oppfolgingsplaner to arkivporten")
        }
    }
}

fun PersistedOppfolgingsplan.toArkivportenDocument(content: ByteArray, dateFormatter: DateTimeFormatter) = Document(
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
