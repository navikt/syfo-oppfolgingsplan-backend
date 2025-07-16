package no.nav.syfo.pdfgen

import java.time.LocalDate

data class OppfolginsplanPdfV1(
    val oppfolgingsplan: Oppfolginsplan
)

data class Oppfolginsplan(
    val version: String = "1.0",
    val sluttDato: LocalDate,
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
