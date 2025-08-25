package no.nav.syfo.oppfolgingsplan.db

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.syfo.defaultPersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.dto.CheckboxFieldSnapshot
import no.nav.syfo.oppfolgingsplan.dto.FormSection
import no.nav.syfo.oppfolgingsplan.dto.FormSnapshot
import no.nav.syfo.oppfolgingsplan.dto.FormSnapshotFieldOption
import no.nav.syfo.oppfolgingsplan.dto.SingleCheckboxFieldSnapshot
import no.nav.syfo.oppfolgingsplan.dto.TextFieldSnapshot
import no.nav.syfo.pdfgen.toOppfolginsplanPdfV1
import java.time.Instant
import java.time.LocalDate

class PersistedOppfolgingsplanTest : DescribeSpec({
    describe("PersistedOppfolgingsplan -> toOppfolginsplanPdfV1") {
        it("should map all fields and sections including radio group and duplicate text fields") {
            val createdAt = Instant.parse("2024-06-01T22:30:00Z") // converts to 2024-06-02 Europe/Oslo (CEST +02)
            val sluttdato = LocalDate.parse("2024-08-15")
            val plan = defaultPersistedOppfolgingsplan().copy(
                createdAt = createdAt,
                sluttdato = sluttdato
            )

            val pdf = plan.toOppfolginsplanPdfV1()

            // Top level mapping
            pdf.version shouldBe "1.0"
            pdf.oppfolgingsplan.createdDate shouldBe LocalDate.parse("2024-06-02")
            pdf.oppfolgingsplan.evaluationDate shouldBe sluttdato
            pdf.oppfolgingsplan.sykmeldtName shouldBe plan.sykmeldtFullName
            pdf.oppfolgingsplan.sykmeldtFnr shouldBe plan.sykmeldtFnr
            pdf.oppfolgingsplan.organisasjonsnavn shouldBe plan.organisasjonsnavn
            pdf.oppfolgingsplan.organisasjonsnummer shouldBe plan.organisasjonsnummer
            pdf.oppfolgingsplan.narmesteLederName shouldBe plan.narmesteLederFullName

            // Sections
            val sections = pdf.oppfolgingsplan.sections
            sections.shouldHaveSize(2)
            sections[0].id shouldBe "arbeidsoppgaver"
            sections[0].title shouldBe "Arbeidsoppgaver"

            // Two text field snapshots are both mapped
            sections[0].inputFields.shouldHaveSize(2)
            sections[0].inputFields[0].id shouldBe "vanligArbeidsdag"
            sections[0].inputFields[0].value shouldBe "Jeg skriver litt om min vanlige arbeidsdag her"
            sections[0].inputFields[1].id shouldBe "ordinæreArbeidsoppgaver"
            sections[0].inputFields[1].value shouldBe "Jeg skriver litt om mine ordinære arbeidsoppgaver her"

            sections[1].id shouldBe "tilpassninger"
            sections[1].title shouldBe "Tilpassninger"
            // Radio group snapshot -> single selected option label
            sections[1].inputFields.shouldHaveSize(1)
            sections[1].inputFields[0].id shouldBe "arbeidsgiver"
            sections[1].inputFields[0].value shouldBe "Dette er option 2"
        }

        it("should map checkbox field values joined with newline in original order") {
            val formSnapshot = FormSnapshot(
                formIdentifier = "oppfolgingsplan",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = listOf(
                    FormSection("s1", "Section 1")
                ),
                fieldSnapshots = listOf(
                    CheckboxFieldSnapshot(
                        fieldId = "cb",
                        sectionId = "s1",
                        label = "Checkbox field",
                        description = null,
                        options = listOf(
                            FormSnapshotFieldOption("a", "A", wasSelected = true),
                            FormSnapshotFieldOption("b", "B", wasSelected = false),
                            FormSnapshotFieldOption("c", "C", wasSelected = true)
                        )
                    )
                )
            )
            val plan = defaultPersistedOppfolgingsplan().copy(content = formSnapshot)

            val pdf = plan.toOppfolginsplanPdfV1()

            val fields = pdf.oppfolgingsplan.sections[0].inputFields
            fields.shouldHaveSize(1)
            fields[0].value shouldBe "A\nC"
        }

        it("should throw when sections are missing") {
            val formSnapshot = FormSnapshot(
                formIdentifier = "oppfolgingsplan",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = null, // triggers IllegalStateException in mapping
                fieldSnapshots = listOf(
                    TextFieldSnapshot(
                        fieldId = "t1",
                        sectionId = null,
                        value = "some text",
                        label = "Label",
                        description = null
                    )
                )
            )
            val plan = defaultPersistedOppfolgingsplan().copy(content = formSnapshot)

            shouldThrow<IllegalStateException> { plan.toOppfolginsplanPdfV1() }
        }

        it("should throw when organisasjonsnavn is null") {
            val plan = defaultPersistedOppfolgingsplan().copy(organisasjonsnavn = null)
            val ex = shouldThrow<RuntimeException> { plan.toOppfolginsplanPdfV1() }
            ex.message shouldBe "Organisasjonsnavn is null"
        }

        it("should throw when narmesteLederFullName is null") {
            val plan = defaultPersistedOppfolgingsplan().copy(narmesteLederFullName = null)
            val ex = shouldThrow<RuntimeException> { plan.toOppfolginsplanPdfV1() }
            ex.message shouldBe "NarmesteLederName is null"
        }

        it("should map SingleCheckboxFieldSnapshot value correctly") {
            val formSnapshot = FormSnapshot(
                formIdentifier = "oppfolgingsplan",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = listOf(
                    FormSection("singlecb", "Single Checkbox Section")
                ),
                fieldSnapshots = listOf(
                    SingleCheckboxFieldSnapshot(
                        fieldId = "singlecb1",
                        label = "Single Checkbox field",
                        description = "desc",
                        sectionId = "singlecb",
                        value = true
                    )
                )
            )
            val plan = defaultPersistedOppfolgingsplan().copy(content = formSnapshot)

            val pdf = plan.toOppfolginsplanPdfV1()
            val fields = pdf.oppfolgingsplan.sections[0].inputFields
            fields.shouldHaveSize(1)
            fields[0].id shouldBe "singlecb1"
            fields[0].title shouldBe "Single Checkbox field"
            fields[0].description shouldBe "desc"
            fields[0].value shouldBe "Ja"
        }
    }
})
