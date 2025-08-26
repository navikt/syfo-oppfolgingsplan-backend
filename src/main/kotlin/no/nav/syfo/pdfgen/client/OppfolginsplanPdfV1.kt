package no.nav.syfo.pdfgen.client

data class OppfolginsplanPdfV1(
    val version: String = "1.0",
    val oppfolgingsplan: Oppfolginsplan
)

data class Oppfolginsplan(
    val createdDate: String,
    val evaluationDate: String,
    val sykmeldtName: String,
    val sykmeldtFnr: String,
    val organisasjonsnavn: String,
    val organisasjonsnummer: String,
    val narmesteLederName: String,
    val sections: List<Section>,
)

data class Section(
    val id: String,
    val title: String,
    val description: String? = null,
    val inputFields: List<InputField>,
)

data class InputField(
    val id: String,
    val title: String,
    val description: String?,
    val value: String,
)
