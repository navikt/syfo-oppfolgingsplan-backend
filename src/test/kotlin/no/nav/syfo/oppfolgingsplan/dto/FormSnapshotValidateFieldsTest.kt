package no.nav.syfo.oppfolgingsplan.dto

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.CheckboxGroupFieldOption
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.CheckboxGroupFieldSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.FormSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.RadioGroupFieldSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.RadioGroupFieldOption
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.Section
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.TextFieldSnapshot

class FormSnapshotValidateFieldsTest : DescribeSpec({
    describe("FormSnapshot structure") {
        it("can create snapshot with sections containing fields") {
            val snapshot = FormSnapshot(
                formIdentifier = "f",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = listOf(
                    Section(
                        sectionId = "s1",
                        sectionTitle = "Section 1",
                        fields = listOf(
                            TextFieldSnapshot(fieldId = "t1", label = "L1", value = "text")
                        )
                    ),
                    Section(
                        sectionId = "s2",
                        sectionTitle = "Section 2",
                        fields = listOf(
                            TextFieldSnapshot(fieldId = "t2", label = "L2", value = "text")
                        )
                    )
                )
            )
            shouldNotThrowAny { snapshot.sections.size shouldBe 2 }
        }

        it("can create snapshot with multiple field types in one section") {
            val snapshot = FormSnapshot(
                formIdentifier = "f",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = listOf(
                    Section(
                        sectionId = "s1",
                        sectionTitle = "Section 1",
                        fields = listOf(
                            TextFieldSnapshot(fieldId = "t1", label = "Label", value = "text"),
                            CheckboxGroupFieldSnapshot(
                                fieldId = "cb1",
                                label = "Label",
                                options = listOf(
                                    CheckboxGroupFieldOption(optionId = "o1", optionLabel = "Opt1", wasSelected = true)
                                )
                            ),
                            RadioGroupFieldSnapshot(
                                fieldId = "rg1",
                                label = "Label",
                                options = listOf(
                                    RadioGroupFieldOption(optionId = "o1", optionLabel = "Opt1"),
                                    RadioGroupFieldOption(optionId = "o2", optionLabel = "Opt2")
                                ),
                                selectedOptionId = "o1",
                            )
                        )
                    )
                )
            )
            shouldNotThrowAny {
                snapshot.sections[0].fields.size shouldBe 3
            }
        }
    }

    describe("TextFieldSnapshot") {
        it("can have blank value when optional") {
            val snapshot = FormSnapshot(
                formIdentifier = "f",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = listOf(
                    Section(
                        sectionId = "s1",
                        sectionTitle = "Section 1",
                        fields = listOf(
                            TextFieldSnapshot(fieldId = "t1", label = "Label", value = "", wasRequired = false)
                        )
                    )
                )
            )
            shouldNotThrowAny {
                val field = snapshot.sections[0].fields[0] as TextFieldSnapshot
                field.value shouldBe ""
            }
        }

        it("can have empty value") {
            val snapshot = FormSnapshot(
                formIdentifier = "f",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = listOf(
                    Section(
                        sectionId = "s1",
                        sectionTitle = "Section 1",
                        fields = listOf(
                            TextFieldSnapshot(fieldId = "t1", label = "Label", value = "", wasRequired = false)
                        )
                    )
                )
            )
            shouldNotThrowAny {
                val field = snapshot.sections[0].fields[0] as TextFieldSnapshot
                field.value shouldBe ""
            }
        }
    }

    describe("CheckboxFieldSnapshot") {
        it("can have no selected options when optional") {
            val snapshot = FormSnapshot(
                formIdentifier = "f",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = listOf(
                    Section(
                        sectionId = "s1",
                        sectionTitle = "Section 1",
                        fields = listOf(
                            CheckboxGroupFieldSnapshot(
                                fieldId = "cb1",
                                label = "Label",
                                options = listOf(
                                    CheckboxGroupFieldOption(optionId = "id1", optionLabel = "Opt1", wasSelected = false)
                                ),
                                wasRequired = false
                            )
                        )
                    )
                )
            )
            shouldNotThrowAny {
                val field = snapshot.sections[0].fields[0] as CheckboxGroupFieldSnapshot
                field.options.none { it.wasSelected } shouldBe true
            }
        }

        it("can have multiple selected options") {
            val snapshot = FormSnapshot(
                formIdentifier = "f",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = listOf(
                    Section(
                        sectionId = "s1",
                        sectionTitle = "Section 1",
                        fields = listOf(
                            CheckboxGroupFieldSnapshot(
                                fieldId = "cb1",
                                label = "Label",
                                options = listOf(
                                    CheckboxGroupFieldOption(optionId = "id1", optionLabel = "Opt1", wasSelected = true),
                                    CheckboxGroupFieldOption(optionId = "id2", optionLabel = "Opt2", wasSelected = true)
                                )
                            )
                        )
                    )
                )
            )
            shouldNotThrowAny {
                val field = snapshot.sections[0].fields[0] as CheckboxGroupFieldSnapshot
                field.options.count { it.wasSelected } shouldBe 2
            }
        }
    }

    describe("RadioGroupFieldSnapshot") {
        it("can have no selected options when optional") {
            val snapshot = FormSnapshot(
                formIdentifier = "f",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = listOf(
                    Section(
                        sectionId = "s1",
                        sectionTitle = "Section 1",
                        fields = listOf(
                            RadioGroupFieldSnapshot(
                                fieldId = "rg1",
                                label = "Label",
                                options = listOf(
                                    RadioGroupFieldOption(optionId = "o1", optionLabel = "Opt1"),
                                    RadioGroupFieldOption(optionId = "o2", optionLabel = "Opt2")
                                ),
                                wasRequired = false
                            )
                        )
                    )
                )
            )
            shouldNotThrowAny {
                val field = snapshot.sections[0].fields[0] as RadioGroupFieldSnapshot
                field.selectedOptionId shouldBe null
            }
        }
    }
})
