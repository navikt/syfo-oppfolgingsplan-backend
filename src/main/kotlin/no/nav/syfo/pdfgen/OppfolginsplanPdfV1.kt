package no.nav.syfo.pdfgen

import java.time.LocalDate

data class OppfolginsplanPdfV1(
    val version: String = "1.0",
    val oppfolgingsplan: Oppfolginsplan
)

data class Oppfolginsplan(
    val createdDate: LocalDate,
    val evaluationDate: LocalDate,
    val sykmeldtName: String,
    val sykmeldtFnr: String,
    val orgName: String,
    val orgnummer: String,
    val narmesteLederName: String,
    val sections: List<Section>,
)

data class Section(
    val id: String,
    val title: String,
    val description: String,
    val textInputFields: List<TextInputField>,
)

data class TextInputField(
    val id: String,
    val title: String,
    val description: String,
    val value: String,
)
