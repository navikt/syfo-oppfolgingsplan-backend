package no.nav.syfo.pdfgen

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.CheckboxGroupFieldSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.DateFieldSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.RadioGroupFieldSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.SingleCheckboxFieldSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.TextFieldSnapshot
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.pdfgen.client.InputField
import no.nav.syfo.pdfgen.client.Oppfolgingsplan
import no.nav.syfo.pdfgen.client.OppfolgingsplanPdfV1
import no.nav.syfo.pdfgen.client.PdfGenClient
import no.nav.syfo.pdfgen.client.Section
import no.nav.syfo.util.logger
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PdfGenService(
    private val pdfGenClient: PdfGenClient,
    private val oppfolgingsplanService: OppfolgingsplanService,
) {
    val logger = logger()
    suspend fun generatePdf(persistedOppfolgingsplan: PersistedOppfolgingsplan): ByteArray? {
        return try {
            val planIncludingName = oppfolgingsplanService.getAndSetNarmestelederFullname(persistedOppfolgingsplan)
            pdfGenClient.generatePdf(planIncludingName.toOppfolgingsplanPdfV1())
        } catch (clientRequestException: ClientRequestException) {
            logger.error("Could not generate pdf for id ${persistedOppfolgingsplan.uuid}")
            throw RuntimeException("Error while generating pdf", clientRequestException)
        } catch (serverResponseException: ServerResponseException) {
            throw RuntimeException("Error while generating pdf", serverResponseException)
        }
    }
}

fun PersistedOppfolgingsplan.toOppfolgingsplanPdfV1(): OppfolgingsplanPdfV1 {
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    return OppfolgingsplanPdfV1(
        oppfolgingsplan = Oppfolgingsplan(
            createdDate = this.createdAt
                .atZone(ZoneId.of("Europe/Oslo"))
                .toLocalDate()
                .format(formatter),
            evaluationDate = this.evalueringsdato.format(formatter),
            sykmeldtName = this.sykmeldtFullName,
            sykmeldtFnr = this.sykmeldtFnr,
            // organisasjonsnavn should always be set, but it is nullable in the response we get from
            // dine-sykmeldte-backend even though all rows in the database currently have a value
            organisasjonsnavn = this.organisasjonsnavn ?: throw RuntimeException("Organisasjonsnavn is null"),
            organisasjonsnummer = this.organisasjonsnummer,
            narmesteLederName = this.narmesteLederFullName ?: throw RuntimeException("NarmesteLederName is null"),
            sections = content.sections.map { section ->
                Section(
                    id = section.sectionId,
                    title = section.sectionTitle,
                    inputFields = section.fields
                        .map { fieldSnapshot ->
                            InputField(
                                id = fieldSnapshot.fieldId,
                                title = fieldSnapshot.label,
                                description = fieldSnapshot.description,
                                value = when (fieldSnapshot) {
                                    is TextFieldSnapshot -> fieldSnapshot.value
                                    is RadioGroupFieldSnapshot -> fieldSnapshot.options
                                        .firstOrNull { it.optionId == fieldSnapshot.selectedOptionId }?.optionLabel
                                        ?: ""

                                    is SingleCheckboxFieldSnapshot -> if (fieldSnapshot.value) "Ja" else "Nei"
                                    is CheckboxGroupFieldSnapshot -> fieldSnapshot.options
                                        .filter { it.wasSelected }
                                        .joinToString("\n") { it.optionLabel }
                                        .ifEmpty { "" }

                                    is DateFieldSnapshot -> fieldSnapshot.value?.format(formatter) ?: ""

                                    else -> throw IllegalArgumentException("Unknown field type: ${fieldSnapshot.fieldType}")
                                }
                            )
                        }
                )
            }
        )
    )
}
