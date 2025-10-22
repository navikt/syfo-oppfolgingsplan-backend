package no.nav.syfo.arkivporten

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.exception.InternalServerErrorException
import no.nav.syfo.arkivporten.client.IArkivportenClient
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanserForArkivportenPublisering
import no.nav.syfo.oppfolgingsplan.db.setPublisertTilArkivportenTidspunkt
import no.nav.syfo.pdfgen.PdfGenService
import no.nav.syfo.util.logger

class ArkivportenService(
    private val arkivportenClient: IArkivportenClient,
    private val database: DatabaseInterface,
    private val pdfGenService: PdfGenService,
) {
    private val dateFormatter = DateTimeFormatter
        .ofPattern("dd.MM.yyyy")
        .withLocale(Locale.forLanguageTag("nb-NO"))
        .withZone(ZoneId.of("Europe/Oslo"))

    private val logger = logger()
    suspend fun finddAndPublishOppfolgingsplaner() {
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
}
