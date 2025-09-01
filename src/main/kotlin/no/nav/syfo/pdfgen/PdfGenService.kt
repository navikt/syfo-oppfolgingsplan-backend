package no.nav.syfo.pdfgen

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.oppfolgingsplan.db.PersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.db.setNarmesteLederFullName
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.CheckboxFieldSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.RadioGroupFieldSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.SingleCheckboxFieldSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.TextFieldSnapshot
import no.nav.syfo.pdfgen.client.Oppfolginsplan
import no.nav.syfo.pdfgen.client.OppfolginsplanPdfV1
import no.nav.syfo.pdfgen.client.PdfGenClient
import no.nav.syfo.pdfgen.client.Section
import no.nav.syfo.pdfgen.client.InputField
import no.nav.syfo.pdl.PdlService
import no.nav.syfo.util.logger
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PdfGenService(
    private val pdfGenClient: PdfGenClient,
    private val database: DatabaseInterface,
    private val pdlService: PdlService
) {
    val logger = logger()
    suspend fun generatePdf(persistedOppfolgingsplan: PersistedOppfolgingsplan): ByteArray? {
        return try {
            if (persistedOppfolgingsplan.narmesteLederFullName.isNullOrEmpty()) {
                val narmesteLederFullName = pdlService.getNameFor(
                    persistedOppfolgingsplan.narmesteLederFnr
                )
                return narmesteLederFullName?.let { narmesteLederName ->
                    database.setNarmesteLederFullName(
                        persistedOppfolgingsplan.uuid,
                        narmesteLederName
                    )
                    val planIncludingName = persistedOppfolgingsplan.copy(narmesteLederFullName = narmesteLederName)
                    pdfGenClient.generatePdf(planIncludingName.toOppfolginsplanPdfV1())
                } ?: run {
                    logger.error("NarmesteLederFullName is null for id ${persistedOppfolgingsplan.uuid}")
                    return null
                }
            } else {
                pdfGenClient.generatePdf(persistedOppfolgingsplan.toOppfolginsplanPdfV1())
            }
        } catch (clientRequestException: ClientRequestException) {
            logger.error("Could not generate pdf for id ${persistedOppfolgingsplan.uuid}")
            throw RuntimeException("Error while generating pdf", clientRequestException)
        } catch (serverResponseException: ServerResponseException) {
            throw RuntimeException("Error while generating pdf", serverResponseException)
        }
    }
}

fun PersistedOppfolgingsplan.toOppfolginsplanPdfV1(): OppfolginsplanPdfV1 {
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    return OppfolginsplanPdfV1(
        oppfolgingsplan = Oppfolginsplan(
            createdDate = this.createdAt
                .atZone(ZoneId.of("Europe/Oslo"))
                .toLocalDate()
                .format(formatter),
            evaluationDate = this.evalueringsdato.format(formatter),
            sykmeldtName = this.sykmeldtFullName,
            sykmeldtFnr = this.sykmeldtFnr,
            // organisasjonsnavn should always be set, but it is nullable in the response we get from dine-sykmeldte-backend
            // even though all rows in the database currently have a value
            organisasjonsnavn = this.organisasjonsnavn ?: throw RuntimeException("Organisasjonsnavn is null"),
            organisasjonsnummer = this.organisasjonsnummer,
            narmesteLederName = this.narmesteLederFullName ?: throw RuntimeException("NarmesteLederName is null"),
            sections = content.sections?.map { section ->
                Section(
                    id = section.sectionId,
                    title = section.sectionTitle,
                    inputFields = content.fieldSnapshots
                        .filter { it.sectionId == section.sectionId }
                        .map { fieldSnapshot ->
                            InputField(
                                id = fieldSnapshot.fieldId,
                                title = fieldSnapshot.label,
                                description = fieldSnapshot.description,
                                value = when (fieldSnapshot) {
                                    is TextFieldSnapshot -> fieldSnapshot.value
                                    is RadioGroupFieldSnapshot -> fieldSnapshot.options.first { it.wasSelected }.optionLabel
                                    is SingleCheckboxFieldSnapshot -> if (fieldSnapshot.value) "Ja" else "Nei"
                                    is CheckboxFieldSnapshot -> fieldSnapshot.options
                                        .filter { it.wasSelected }
                                        .joinToString("\n") { it.optionLabel }

                                    else -> throw IllegalArgumentException("Unknown field type: ${fieldSnapshot.fieldType}")
                                }
                            )
                        }
                )
            } ?: throw IllegalStateException("Missing sections in content")
        )
    )
}
