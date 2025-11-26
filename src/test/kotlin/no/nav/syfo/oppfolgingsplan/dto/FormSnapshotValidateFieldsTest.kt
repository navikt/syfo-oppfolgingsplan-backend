package no.nav.syfo.oppfolgingsplan.dto

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.CheckboxFieldOption
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.CheckboxFieldSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.FormSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.RadioGroupFieldSnapshot
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.RadiogroupFieldOption
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
                        sectionFields = listOf(
                            TextFieldSnapshot(fieldId = "t1", label = "L1", value = "text")
                        )
                    ),
                    Section(
                        sectionId = "s2",
                        sectionTitle = "Section 2",
                        sectionFields = listOf(
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
                        sectionFields = listOf(
                            TextFieldSnapshot(fieldId = "t1", label = "Label", value = "text"),
                            CheckboxFieldSnapshot(
                                fieldId = "cb1",
                                label = "Label",
                                options = listOf(
                                    CheckboxFieldOption(optionId = "o1", optionLabel = "Opt1", wasSelected = true)
                                )
                            ),
                            RadioGroupFieldSnapshot(
                                fieldId = "rg1",
                                label = "Label",
                                options = listOf(
                                    RadiogroupFieldOption(optionId = "o1", optionLabel = "Opt1"),
                                    RadiogroupFieldOption(optionId = "o2", optionLabel = "Opt2")
                                ),
                                selectedOptionId = "o1",
                            )
                        )
                    )
                )
            )
            shouldNotThrowAny {
                snapshot.sections[0].sectionFields.size shouldBe 3
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
                        sectionFields = listOf(
                            TextFieldSnapshot(fieldId = "t1", label = "Label", value = "", wasRequired = false)
                        )
                    )
                )
            )
            shouldNotThrowAny {
                val field = snapshot.sections[0].sectionFields[0] as TextFieldSnapshot
                field.value shouldBe ""
            }
        }

        it("can have null value") {
            val snapshot = FormSnapshot(
                formIdentifier = "f",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = listOf(
                    Section(
                        sectionId = "s1",
                        sectionTitle = "Section 1",
                        sectionFields = listOf(
                            TextFieldSnapshot(fieldId = "t1", label = "Label", value = null, wasRequired = false)
                        )
                    )
                )
            )
            shouldNotThrowAny {
                val field = snapshot.sections[0].sectionFields[0] as TextFieldSnapshot
                field.value shouldBe null
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
                        sectionFields = listOf(
                            CheckboxFieldSnapshot(
                                fieldId = "cb1",
                                label = "Label",
                                options = listOf(
                                    CheckboxFieldOption(optionId = "id1", optionLabel = "Opt1", wasSelected = false)
                                ),
                                wasRequired = false
                            )
                        )
                    )
                )
            )
            shouldNotThrowAny {
                val field = snapshot.sections[0].sectionFields[0] as CheckboxFieldSnapshot
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
                        sectionFields = listOf(
                            CheckboxFieldSnapshot(
                                fieldId = "cb1",
                                label = "Label",
                                options = listOf(
                                    CheckboxFieldOption(optionId = "id1", optionLabel = "Opt1", wasSelected = true),
                                    CheckboxFieldOption(optionId = "id2", optionLabel = "Opt2", wasSelected = true)
                                )
                            )
                        )
                    )
                )
            )
            shouldNotThrowAny {
                val field = snapshot.sections[0].sectionFields[0] as CheckboxFieldSnapshot
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
                        sectionFields = listOf(
                            RadioGroupFieldSnapshot(
                                fieldId = "rg1",
                                label = "Label",
                                options = listOf(
                                    RadiogroupFieldOption(optionId = "o1", optionLabel = "Opt1"),
                                    RadiogroupFieldOption(optionId = "o2", optionLabel = "Opt2")
                                ),
                                wasRequired = false
                            )
                        )
                    )
                )
            )
            shouldNotThrowAny {
                val field = snapshot.sections[0].sectionFields[0] as RadioGroupFieldSnapshot
                field.selectedOptionId shouldBe null
            }
        }
    }
})
