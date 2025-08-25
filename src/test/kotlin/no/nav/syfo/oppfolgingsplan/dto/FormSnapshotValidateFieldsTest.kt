package no.nav.syfo.oppfolgingsplan.dto

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class FormSnapshotValidateFieldsTest : DescribeSpec({
    describe("FormSnapshot.validateFields - sections") {
        it("passes when all sectionIds match defined sections") {
            val snapshot = FormSnapshot(
                formIdentifier = "f",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = listOf(
                    FormSection("s1", "Section 1"),
                    FormSection("s2", "Section 2")
                ),
                fieldSnapshots = listOf(
                    TextFieldSnapshot("t1", label = "L1", sectionId = "s1", value = "text"),
                    TextFieldSnapshot("t2", label = "L2", sectionId = "s2", value = "text")
                )
            )
            shouldNotThrowAny { snapshot.validateFields() }
        }
        it("fails when a field references a non-existing section") {
            val snapshot = FormSnapshot(
                formIdentifier = "f",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = listOf(FormSection("s1", "Section 1")),
                fieldSnapshots = listOf(
                    TextFieldSnapshot("t1", label = "L1", sectionId = "does-not-exist", value = "text")
                )
            )
            val ex = shouldThrow<IllegalArgumentException> { snapshot.validateFields() }
            ex.message shouldBe "field with fieldId t1 has sectionId does-not-exist which does not match any section"
        }
    }

    describe("TextFieldSnapshot validation") {
        it("fails when required text field value is blank") {
            val snapshot = FormSnapshot(
                formIdentifier = "f",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = null,
                fieldSnapshots = listOf(
                    TextFieldSnapshot("t1", label = "Label", value = "", wasRequired = true)
                )
            )
            val ex = shouldThrow<IllegalArgumentException> { snapshot.validateFields() }
            ex.message shouldBe "Text field value must not be blank"
        }
        it("passes when optional text field value is blank") {
            val snapshot = FormSnapshot(
                formIdentifier = "f",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = null,
                fieldSnapshots = listOf(
                    TextFieldSnapshot("t1", label = "Label", value = "", wasRequired = false)
                )
            )
            shouldNotThrowAny { snapshot.validateFields() }
        }
    }

    describe("CheckboxFieldSnapshot validation") {
        it("fails when no options provided") {
            val snapshot = FormSnapshot(
                formIdentifier = "f",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = null,
                fieldSnapshots = listOf(
                    CheckboxFieldSnapshot(
                        fieldId = "cb1",
                        label = "Label",
                        options = emptyList(),
                        sectionId = null,
                        description = null
                    )
                )
            )
            val ex = shouldThrow<IllegalArgumentException> { snapshot.validateFields() }
            ex.message shouldBe "Checkbox field must have at least one option"
        }
        it("fails when any option id is blank") {
            val snapshot = FormSnapshot(
                formIdentifier = "f",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = null,
                fieldSnapshots = listOf(
                    CheckboxFieldSnapshot(
                        fieldId = "cb1",
                        label = "Label",
                        options = listOf(
                            FormSnapshotFieldOption(optionId = "", optionLabel = "Lbl", wasSelected = true)
                        )
                    )
                )
            )
            val ex = shouldThrow<IllegalArgumentException> { snapshot.validateFields() }
            ex.message shouldBe "optionId must not be blank"
        }
        it("fails when any option label is blank") {
            val snapshot = FormSnapshot(
                formIdentifier = "f",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = null,
                fieldSnapshots = listOf(
                    CheckboxFieldSnapshot(
                        fieldId = "cb1",
                        label = "Label",
                        options = listOf(
                            FormSnapshotFieldOption(optionId = "id", optionLabel = "", wasSelected = true)
                        )
                    )
                )
            )
            val ex = shouldThrow<IllegalArgumentException> { snapshot.validateFields() }
            ex.message shouldBe "optionLabel must not be blank"
        }
        it("fails when required checkbox field has no selected options") {
            val snapshot = FormSnapshot(
                formIdentifier = "f",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = null,
                fieldSnapshots = listOf(
                    CheckboxFieldSnapshot(
                        fieldId = "cb1",
                        label = "Label",
                        options = listOf(
                            FormSnapshotFieldOption(optionId = "id1", optionLabel = "Opt1", wasSelected = false)
                        ),
                        wasRequired = true
                    )
                )
            )
            val ex = shouldThrow<IllegalArgumentException> { snapshot.validateFields() }
            ex.message shouldBe "At least one option must be selected for a required checkbox field"
        }
        it("passes when optional checkbox field has no selection") {
            val snapshot = FormSnapshot(
                formIdentifier = "f",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = null,
                fieldSnapshots = listOf(
                    CheckboxFieldSnapshot(
                        fieldId = "cb1",
                        label = "Label",
                        options = listOf(
                            FormSnapshotFieldOption(optionId = "id1", optionLabel = "Opt1", wasSelected = false)
                        ),
                        wasRequired = false
                    )
                )
            )
            shouldNotThrowAny { snapshot.validateFields() }
        }
    }

    describe("RadioGroupFieldSnapshot validation") {
        it("fails when fewer than two options") {
            val snapshot = FormSnapshot(
                formIdentifier = "f",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = null,
                fieldSnapshots = listOf(
                    RadioGroupFieldSnapshot(
                        fieldId = "rg1",
                        label = "Label",
                        options = listOf(
                            FormSnapshotFieldOption(optionId = "o1", optionLabel = "Opt1", wasSelected = true)
                        )
                    )
                )
            )
            val ex = shouldThrow<IllegalArgumentException> { snapshot.validateFields() }
            ex.message shouldBe "Radio group field must have at least two options"
        }
        it("fails when option label blank") {
            val snapshot = FormSnapshot(
                formIdentifier = "f",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = null,
                fieldSnapshots = listOf(
                    RadioGroupFieldSnapshot(
                        fieldId = "rg1",
                        label = "Label",
                        options = listOf(
                            FormSnapshotFieldOption(optionId = "o1", optionLabel = "", wasSelected = true),
                            FormSnapshotFieldOption(optionId = "o2", optionLabel = "Opt2", wasSelected = false)
                        )
                    )
                )
            )
            val ex = shouldThrow<IllegalArgumentException> { snapshot.validateFields() }
            ex.message shouldBe "optionLabel must not be blank"
        }
        it("fails when required and zero selected") {
            val snapshot = FormSnapshot(
                formIdentifier = "f",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = null,
                fieldSnapshots = listOf(
                    RadioGroupFieldSnapshot(
                        fieldId = "rg1",
                        label = "Label",
                        options = listOf(
                            FormSnapshotFieldOption(optionId = "o1", optionLabel = "Opt1", wasSelected = false),
                            FormSnapshotFieldOption(optionId = "o2", optionLabel = "Opt2", wasSelected = false)
                        ),
                        wasRequired = true
                    )
                )
            )
            val ex = shouldThrow<IllegalArgumentException> { snapshot.validateFields() }
            ex.message shouldBe "Exactly one option must be selected for a required radio group field"
        }
        it("fails when required and more than one selected") {
            val snapshot = FormSnapshot(
                formIdentifier = "f",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = null,
                fieldSnapshots = listOf(
                    RadioGroupFieldSnapshot(
                        fieldId = "rg1",
                        label = "Label",
                        options = listOf(
                            FormSnapshotFieldOption(optionId = "o1", optionLabel = "Opt1", wasSelected = true),
                            FormSnapshotFieldOption(optionId = "o2", optionLabel = "Opt2", wasSelected = true)
                        ),
                        wasRequired = true
                    )
                )
            )
            val ex = shouldThrow<IllegalArgumentException> { snapshot.validateFields() }
            ex.message shouldBe "Exactly one option must be selected for a required radio group field"
        }
        it("passes when exactly one selected and valid") {
            val snapshot = FormSnapshot(
                formIdentifier = "f",
                formSemanticVersion = "1.0.0",
                formSnapshotVersion = "2.0.0",
                sections = null,
                fieldSnapshots = listOf(
                    RadioGroupFieldSnapshot(
                        fieldId = "rg1",
                        label = "Label",
                        options = listOf(
                            FormSnapshotFieldOption(optionId = "o1", optionLabel = "Opt1", wasSelected = true),
                            FormSnapshotFieldOption(optionId = "o2", optionLabel = "Opt2", wasSelected = false)
                        ),
                        wasRequired = true
                    )
                )
            )
            shouldNotThrowAny { snapshot.validateFields() }
        }
    }
})
