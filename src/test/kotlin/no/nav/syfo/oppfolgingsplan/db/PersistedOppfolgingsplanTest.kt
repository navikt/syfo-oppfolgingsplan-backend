package no.nav.syfo.oppfolgingsplan.db

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.syfo.defaultFormSnapshot
import no.nav.syfo.defaultPersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.db.domain.toSykmeldtOppfolgingsplanOverviewResponse
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.CheckboxGroupFieldOption
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.CheckboxGroupFieldSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.DateFieldSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.FormSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.Section
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.SingleCheckboxFieldSnapshot
import no.nav.syfo.pdfgen.toOppfolgingsplanPdfV1
import java.time.Instant
import java.time.LocalDate
import java.util.*

class PersistedOppfolgingsplanTest : DescribeSpec({

    describe("toSykmeldtOppfolgingsplanOverviewResponse") {
        it("should return empty lists when no plans exist") {
            val result = emptyList<PersistedOppfolgingsplan>()
                .toSykmeldtOppfolgingsplanOverviewResponse()

            result.aktiveOppfolgingsplaner.shouldBeEmpty()
            result.tidligerePlaner.shouldBeEmpty()
        }

        it("should return single plan as active when only one plan exists") {
            val plan = defaultPersistedOppfolgingsplan()
            val result = listOf(plan).toSykmeldtOppfolgingsplanOverviewResponse()

            result.aktiveOppfolgingsplaner shouldHaveSize 1
            result.aktiveOppfolgingsplaner[0].id shouldBe plan.uuid
            result.tidligerePlaner.shouldBeEmpty()
        }

        it("should return newest plan as active and older as tidligere for single org") {
            val olderPlan = defaultPersistedOppfolgingsplan().copy(
                uuid = UUID.randomUUID(),
                createdAt = Instant.parse("2024-01-01T10:00:00Z")
            )
            val newerPlan = defaultPersistedOppfolgingsplan().copy(
                uuid = UUID.randomUUID(),
                createdAt = Instant.parse("2024-06-01T10:00:00Z")
            )

            val result = listOf(olderPlan, newerPlan).toSykmeldtOppfolgingsplanOverviewResponse()

            result.aktiveOppfolgingsplaner shouldHaveSize 1
            result.aktiveOppfolgingsplaner[0].id shouldBe newerPlan.uuid
            result.tidligerePlaner shouldHaveSize 1
            result.tidligerePlaner[0].id shouldBe olderPlan.uuid
        }

        it("should return newest plan per org as active when multiple orgs") {
            val org1Plan1 = defaultPersistedOppfolgingsplan().copy(
                uuid = UUID.randomUUID(),
                organisasjonsnummer = "org1",
                createdAt = Instant.parse("2024-01-01T10:00:00Z")
            )
            val org1Plan2 = defaultPersistedOppfolgingsplan().copy(
                uuid = UUID.randomUUID(),
                organisasjonsnummer = "org1",
                createdAt = Instant.parse("2024-06-01T10:00:00Z")
            )
            val org2Plan1 = defaultPersistedOppfolgingsplan().copy(
                uuid = UUID.randomUUID(),
                organisasjonsnummer = "org2",
                createdAt = Instant.parse("2024-03-01T10:00:00Z")
            )

            val result = listOf(org1Plan1, org1Plan2, org2Plan1).toSykmeldtOppfolgingsplanOverviewResponse()

            result.aktiveOppfolgingsplaner shouldHaveSize 2
            result.aktiveOppfolgingsplaner.map { it.id } shouldContainExactlyInAnyOrder listOf(
                org1Plan2.uuid,
                org2Plan1.uuid
            )
            result.tidligerePlaner shouldHaveSize 1
            result.tidligerePlaner[0].id shouldBe org1Plan1.uuid
        }

        it("should handle multiple orgs with multiple plans each") {
            val org1Oldest = defaultPersistedOppfolgingsplan().copy(
                uuid = UUID.randomUUID(),
                organisasjonsnummer = "org1",
                createdAt = Instant.parse("2023-01-01T10:00:00Z")
            )
            val org1Middle = defaultPersistedOppfolgingsplan().copy(
                uuid = UUID.randomUUID(),
                organisasjonsnummer = "org1",
                createdAt = Instant.parse("2024-01-01T10:00:00Z")
            )
            val org1Newest = defaultPersistedOppfolgingsplan().copy(
                uuid = UUID.randomUUID(),
                organisasjonsnummer = "org1",
                createdAt = Instant.parse("2024-06-01T10:00:00Z")
            )
            val org2Oldest = defaultPersistedOppfolgingsplan().copy(
                uuid = UUID.randomUUID(),
                organisasjonsnummer = "org2",
                createdAt = Instant.parse("2023-06-01T10:00:00Z")
            )
            val org2Newest = defaultPersistedOppfolgingsplan().copy(
                uuid = UUID.randomUUID(),
                organisasjonsnummer = "org2",
                createdAt = Instant.parse("2024-12-01T10:00:00Z")
            )

            val result = listOf(org1Oldest, org1Middle, org1Newest, org2Oldest, org2Newest)
                .toSykmeldtOppfolgingsplanOverviewResponse()

            result.aktiveOppfolgingsplaner shouldHaveSize 2
            result.aktiveOppfolgingsplaner.map { it.id } shouldContainExactlyInAnyOrder listOf(
                org1Newest.uuid,
                org2Newest.uuid
            )

            result.tidligerePlaner shouldHaveSize 3
            result.tidligerePlaner.map { it.id } shouldContainExactlyInAnyOrder listOf(
                org1Oldest.uuid,
                org1Middle.uuid,
                org2Oldest.uuid
            )
        }

        it("should handle single plan per multiple orgs - all should be active") {
            val org1Plan = defaultPersistedOppfolgingsplan().copy(
                uuid = UUID.randomUUID(),
                organisasjonsnummer = "org1"
            )
            val org2Plan = defaultPersistedOppfolgingsplan().copy(
                uuid = UUID.randomUUID(),
                organisasjonsnummer = "org2"
            )
            val org3Plan = defaultPersistedOppfolgingsplan().copy(
                uuid = UUID.randomUUID(),
                organisasjonsnummer = "org3"
            )

            val result = listOf(org1Plan, org2Plan, org3Plan).toSykmeldtOppfolgingsplanOverviewResponse()

            result.aktiveOppfolgingsplaner shouldHaveSize 3
            result.aktiveOppfolgingsplaner.map { it.id } shouldContainExactlyInAnyOrder listOf(
                org1Plan.uuid,
                org2Plan.uuid,
                org3Plan.uuid
            )
            result.tidligerePlaner.shouldBeEmpty()
        }
    }

    describe("PersistedOppfolgingsplan -> toOppfolgingsplanPdfV1") {
        it("should map all fields and sections including radio group and duplicate text fields") {
            val createdAt = Instant.parse("2024-06-01T22:30:00Z") // converts to 2024-06-02 Europe/Oslo (CEST +02)
            val evalueringsdato = LocalDate.parse("2024-08-15")

            val formSnapshot = defaultFormSnapshot().copy(
                sections = defaultFormSnapshot().sections.map { section ->
                    section.copy(
                        fields = section.fields.map { field ->
                            if (field is DateFieldSnapshot && field.fieldId == "evalueringsDato") {
                                field.copy(value = evalueringsdato)
                            } else {
                                field
                            }
                        }
                    )
                }
            )

            val plan = defaultPersistedOppfolgingsplan().copy(
                createdAt = createdAt,
                evalueringsdato = evalueringsdato,
                content = formSnapshot
            )

            val pdf = plan.toOppfolgingsplanPdfV1()

            // Top level mapping
            pdf.version shouldBe "1.0"
            pdf.oppfolgingsplan.createdDate shouldBe "02.06.2024"
            pdf.oppfolgingsplan.evaluationDate shouldBe "15.08.2024"
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

            sections[1].id shouldBe "tilpasninger"
            sections[1].title shouldBe "Tilpasninger"
            // Radio group snapshot -> single selected option label
            sections[1].inputFields.shouldHaveSize(2)
            sections[1].inputFields[0].id shouldBe "arbeidsgiver"
            sections[1].inputFields[0].value shouldBe "Dette er option 2"
            // DateFieldSnapshot should be formatted as dd.MM.yyyy
            sections[1].inputFields[1].id shouldBe "evalueringsDato"
            sections[1].inputFields[1].title shouldBe "Evalueringsdato"
            sections[1].inputFields[1].value shouldBe "15.08.2024"
        }

        it("should map checkbox field values joined with newline in original order") {
            val formSnapshot = FormSnapshot(
                formIdentifier = "oppfolgingsplan",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = listOf(
                    Section(
                        sectionId = "s1",
                        sectionTitle = "Section 1",
                        fields = listOf(
                            CheckboxGroupFieldSnapshot(
                                fieldId = "cb",
                                label = "Checkbox field",
                                description = null,
                                options = listOf(
                                    CheckboxGroupFieldOption("a", "A", wasSelected = true),
                                    CheckboxGroupFieldOption("b", "B", wasSelected = false),
                                    CheckboxGroupFieldOption("c", "C", wasSelected = true)
                                )
                            )
                        )
                    )
                )
            )
            val plan = defaultPersistedOppfolgingsplan().copy(content = formSnapshot)

            val pdf = plan.toOppfolgingsplanPdfV1()

            val fields = pdf.oppfolgingsplan.sections[0].inputFields
            fields.shouldHaveSize(1)
            fields[0].value shouldBe "A\nC"
        }

        it("should handle empty sections list") {
            val formSnapshot = FormSnapshot(
                formIdentifier = "oppfolgingsplan",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = emptyList()
            )
            val plan = defaultPersistedOppfolgingsplan().copy(content = formSnapshot)

            val pdf = plan.toOppfolgingsplanPdfV1()

            pdf.oppfolgingsplan.sections.shouldHaveSize(0)
        }

        it("should throw when organisasjonsnavn is null") {
            val plan = defaultPersistedOppfolgingsplan().copy(organisasjonsnavn = null)
            val ex = shouldThrow<RuntimeException> { plan.toOppfolgingsplanPdfV1() }
            ex.message shouldBe "Organisasjonsnavn is null"
        }

        it("should throw when narmesteLederFullName is null") {
            val plan = defaultPersistedOppfolgingsplan().copy(narmesteLederFullName = null)
            val ex = shouldThrow<RuntimeException> { plan.toOppfolgingsplanPdfV1() }
            ex.message shouldBe "NarmesteLederName is null"
        }

        it("should map SingleCheckboxFieldSnapshot value correctly") {
            val formSnapshot = FormSnapshot(
                formIdentifier = "oppfolgingsplan",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = listOf(
                    Section(
                        sectionId = "singlecb",
                        sectionTitle = "Single Checkbox Section",
                        fields = listOf(
                            SingleCheckboxFieldSnapshot(
                                fieldId = "singlecb1",
                                label = "Single Checkbox field",
                                description = "desc",
                                value = true
                            )
                        )
                    )
                )
            )
            val plan = defaultPersistedOppfolgingsplan().copy(content = formSnapshot)

            val pdf = plan.toOppfolgingsplanPdfV1()
            val fields = pdf.oppfolgingsplan.sections[0].inputFields
            fields.shouldHaveSize(1)
            fields[0].id shouldBe "singlecb1"
            fields[0].title shouldBe "Single Checkbox field"
            fields[0].description shouldBe "desc"
            fields[0].value shouldBe "Ja"
        }

        it("should format DateFieldSnapshot value as dd.MM.yyyy in PDF") {
            val testDate = LocalDate.of(2025, 11, 25)
            val formSnapshot = FormSnapshot(
                formIdentifier = "oppfolgingsplan",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = listOf(
                    Section(
                        sectionId = "dateSection",
                        sectionTitle = "Date Section",
                        fields = listOf(
                            DateFieldSnapshot(
                                fieldId = "testDate",
                                label = "Test Date",
                                description = "A test date",
                                value = testDate,
                                wasRequired = true
                            )
                        )
                    )
                )
            )
            val plan = defaultPersistedOppfolgingsplan().copy(content = formSnapshot)

            val pdf = plan.toOppfolgingsplanPdfV1()

            val fields = pdf.oppfolgingsplan.sections[0].inputFields
            fields.shouldHaveSize(1)
            fields[0].id shouldBe "testDate"
            fields[0].title shouldBe "Test Date"
            fields[0].value shouldBe "25.11.2025"
        }
    }
})
