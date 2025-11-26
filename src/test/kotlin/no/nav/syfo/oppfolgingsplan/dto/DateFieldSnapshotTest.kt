package no.nav.syfo.oppfolgingsplan.dto

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.DateFieldSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.FormSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.FormSnapshotFieldType
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.Section
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.SingleCheckboxFieldSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.TextFieldSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.jsonToFormSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.toJsonString
import java.time.LocalDate

class DateFieldSnapshotTest : DescribeSpec({
    describe("DateFieldSnapshot serialization and deserialization") {
        it("should serialize and deserialize DateFieldSnapshot with LocalDate") {
            val originalDate = LocalDate.of(2025, 11, 25)
            val originalSnapshot = FormSnapshot(
                formIdentifier = "test-form",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = listOf(
                    Section(
                        sectionId = "dates",
                        sectionTitle = "Date Section",
                        fields = listOf(
                            DateFieldSnapshot(
                                fieldId = "testDate",
                                label = "Test Date",
                                description = "A test date field",
                                value = originalDate,
                                wasRequired = true
                            )
                        )
                    )
                )
            )

            // Serialize to JSON
            val jsonString = originalSnapshot.toJsonString()

            // Verify JSON contains the date in ISO format
            jsonString.contains("2025-11-25") shouldBe true
            jsonString.contains("\"fieldType\":\"DATE\"") shouldBe true

            // Deserialize back
            val deserializedSnapshot = FormSnapshot.jsonToFormSnapshot(jsonString)

            // Verify the snapshot is correctly deserialized
            deserializedSnapshot.sections.size shouldBe 1
            deserializedSnapshot.sections[0].fields.size shouldBe 1
            val dateField = deserializedSnapshot.sections[0].fields[0] as DateFieldSnapshot
            dateField.fieldId shouldBe "testDate"
            dateField.label shouldBe "Test Date"
            dateField.description shouldBe "A test date field"
            dateField.value shouldBe originalDate
            dateField.wasRequired shouldBe true
            dateField.fieldType shouldBe FormSnapshotFieldType.DATE
        }

        it("should handle multiple date fields in same form") {
            val date1 = LocalDate.of(2025, 1, 1)
            val date2 = LocalDate.of(2025, 12, 31)
            val snapshot = FormSnapshot(
                formIdentifier = "multi-date-form",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = listOf(
                    Section(
                        sectionId = "s1",
                        sectionTitle = "Section 1",
                        fields = listOf(
                            DateFieldSnapshot(
                                fieldId = "startDate",
                                label = "Start Date",
                                value = date1,
                                wasRequired = true
                            ),
                            DateFieldSnapshot(
                                fieldId = "endDate",
                                label = "End Date",
                                value = date2,
                                wasRequired = false
                            )
                        )
                    )
                )
            )

            val jsonString = snapshot.toJsonString()
            val deserialized = FormSnapshot.jsonToFormSnapshot(jsonString)

            deserialized.sections.size shouldBe 1
            deserialized.sections[0].fields.size shouldBe 2
            val startDateField = deserialized.sections[0].fields[0] as DateFieldSnapshot
            val endDateField = deserialized.sections[0].fields[1] as DateFieldSnapshot

            startDateField.value shouldBe date1
            startDateField.wasRequired shouldBe true
            endDateField.value shouldBe date2
            endDateField.wasRequired shouldBe false
        }

        it("should preserve date field alongside other field types") {
            val testDate = LocalDate.of(2025, 6, 15)
            val snapshot = FormSnapshot(
                formIdentifier = "mixed-form",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = listOf(
                    Section(
                        sectionId = "s1",
                        sectionTitle = "Section 1",
                        fields = listOf(
                            TextFieldSnapshot(
                                fieldId = "text1",
                                label = "Text Field",
                                value = "Some text"
                            ),
                            DateFieldSnapshot(
                                fieldId = "date1",
                                label = "Date Field",
                                value = testDate
                            ),
                            SingleCheckboxFieldSnapshot(
                                fieldId = "checkbox1",
                                label = "Checkbox",
                                value = true
                            )
                        )
                    )
                )
            )

            val jsonString = snapshot.toJsonString()
            val deserialized = FormSnapshot.jsonToFormSnapshot(jsonString)

            deserialized.sections.size shouldBe 1
            deserialized.sections[0].fields.size shouldBe 3
            val dateField = deserialized.sections[0].fields[1] as DateFieldSnapshot
            dateField.value shouldBe testDate
        }
    }
})

