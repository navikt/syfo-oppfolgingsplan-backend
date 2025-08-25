package no.nav.syfo.oppfolgingsplan.dto

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import java.io.Serializable

// The kdoc comments are written in regard to FormSnapshot being used in a general context,
// not specifically for the motebehov use case.

/**
 * FormSnapshot is a data class describing details of some simple form and how it was filled out in a form submission.
 *
 * FormSnapshot can be used as a DTO for transmitting a form snapshot between frontend and backend, and as a
 * serializable storage format. The main use case for the FormSnapshot format is to support storage and display of form
 * responses for forms where at least some of these conditions apply:
 * - The form will probably change over time (for example with labels changing, or fields being added or removed).
 * - The form responses are mainly submitted in order to be displayed to humans (as opposed to mainly being read
 *   programmatically).
 * - The users viewing the form responses are interested in seeing what the form looked like at the time of submission,
 *   as opposed to what the form looks like currently, i.e. in case of a label change, or in case of a more drastic
 *   change.
 * - The users viewing the form response might be interested in seeing all the options that was available to choose from
 *   for a radio buttons field, as opposed to just the selected option.
 *
 * The contents of a form snapshot might be displayed to the "form filling" user themselves on a receipt screeen,
 * or to a veileder in Modia.
 *
 * A form snapshot is meant to describe what a form looked like at the time of submission, much like a paper copy of a
 * filled out form. It describes which fields the form consisted of and their types, labels, etc. A stored FormSnapshot
 * preserves this data, so that this data doesn't have to be stored elsewhere. If a form is changed, a form snapshot for
 * an earlier version of the form will still be valid and describe the form at the time of submission.
 *
 * A form snapshot consists of a list of *snapshot fields*. All snapshot fields have an id, a label, and a type.
 * The type of a field can be one of the following:
 * - TEXT: A field where the user could input text.
 * - CHECKBOX: A checkbox field.
 * - RADIO_GROUP: A radio buttons field where the user could select one of multiple options.
 */
data class FormSnapshot(
    /** An identifier or name identifying which form this is snapshot is for. */
    val formIdentifier: String,
    /** This version tag can be used to signify which version of a form a FormSnapshot is for, and how much is
     *  changed between two versions. If a label text is changed, it might be denoted with a patch version bump. If the
     *  ordering of the fields are changed, or the set of options for a radioGroup field is changed, it might count as a
     *  minor version bump. If the set of fieldIds for a form is changed, which can happen if new fields are added or
     *  existing fields are removed, or if an existing fieldId is changed, it might count as a major version bump. */
    val formSemanticVersion: String,
    /** The version of the form snapshot format itself. */
    val formSnapshotVersion: String,
    // For info: This configures deserialization both for POST-handlers in controllers and for the object mapper used
    // when reading from the database in FormSnapshotJSONConversion.kt.
    @JsonDeserialize(contentUsing = FieldSnapshotDeserializer::class)
    val fieldSnapshots: List<FieldSnapshot>,

    val sections: List<FormSection>? = null

) {
    companion object

    fun validateFields() {
        fieldSnapshots.forEach { fieldSnapshot ->
            require(fieldSnapshot.fieldId.isNotBlank()) { "fieldId must not be blank" }
            require(fieldSnapshot.label.isNotBlank()) { "label must not be blank" }

            if (fieldSnapshot.sectionId?.isNotBlank() == true) {
                require(sections?.any { it.sectionId == fieldSnapshot.sectionId } == true) {
                    "field with fieldId ${fieldSnapshot.fieldId} has sectionId ${fieldSnapshot.sectionId} which does not match any section"
                }
            }
            when (fieldSnapshot) {
                is TextFieldSnapshot -> {
                    if (fieldSnapshot.wasRequired == true) {
                        require(fieldSnapshot.value.isNotBlank()) { "Text field value must not be blank" }
                    }
                }
                is CheckboxFieldSnapshot -> {
                    require(fieldSnapshot.options.isNotEmpty()) { "Checkbox field must have at least one option" }
                    fieldSnapshot.options.forEach { option ->
                        require(option.optionId.isNotBlank()) { "optionId must not be blank" }
                        require(option.optionLabel.isNotBlank()) { "optionLabel must not be blank" }
                    }
                    if (fieldSnapshot.wasRequired == true) {
                        require(fieldSnapshot.options.any { it.wasSelected }) {
                            "At least one option must be selected for a required checkbox field"
                        }
                    }
                }

                is RadioGroupFieldSnapshot -> {
                    require(fieldSnapshot.options.size >= 2) { "Radio group field must have at least two options" }
                    fieldSnapshot.options.forEach { option ->
                        require(option.optionId.isNotBlank()) { "optionId must not be blank" }
                        require(option.optionLabel.isNotBlank()) { "optionLabel must not be blank" }
                    }
                    if (fieldSnapshot.wasRequired == true) {
                        require(fieldSnapshot.options.count { it.wasSelected } == 1) {
                            "Exactly one option must be selected for a required radio group field"
                        }
                    }
                }
            }
        }
    }
}

abstract class FieldSnapshot(
    open val fieldId: String,
    open val fieldType: FormSnapshotFieldType,
    open val label: String,
    open val description: String? = null,
    open val sectionId: String? = null,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

data class TextFieldSnapshot(
    override val fieldId: String,
    override val label: String,
    override val description: String? = null,
    override val sectionId: String? = null,
    val value: String,
    val wasRequired: Boolean? = true,
) : FieldSnapshot(fieldId, fieldType = FormSnapshotFieldType.TEXT, label, description, sectionId)

data class SingleCheckboxFieldSnapshot(
    override val fieldId: String,
    override val label: String,
    override val description: String? = null,
    override val sectionId: String? = null,
    val value: Boolean,
) : FieldSnapshot(fieldId, fieldType = FormSnapshotFieldType.CHECKBOX_SINGLE, label, description)

data class CheckboxFieldSnapshot(
    override val fieldId: String,
    override val label: String,
    override val description: String? = null,
    override val sectionId: String? = null,
    val options: List<FormSnapshotFieldOption>,
    val wasRequired: Boolean? = true,
) : FieldSnapshot(fieldId, fieldType = FormSnapshotFieldType.CHECKBOX, label, description, sectionId)

data class RadioGroupFieldSnapshot(
    override val fieldId: String,
    override val label: String,
    override val description: String? = null,
    override val sectionId: String? = null,
    val options: List<FormSnapshotFieldOption>,
    val wasRequired: Boolean? = true,
) : FieldSnapshot(fieldId, fieldType = FormSnapshotFieldType.RADIO_GROUP, label, description, sectionId)

data class FormSnapshotFieldOption(
    val optionId: String,
    val optionLabel: String,
    val wasSelected: Boolean = false,
)

enum class FormSnapshotFieldType(val type: String) {
    TEXT("text"),
    CHECKBOX_SINGLE("checkboxSingle"),
    CHECKBOX("checkbox"),
    RADIO_GROUP("radioGroup")
}

data class FormSection(
    val sectionId: String,
    val sectionTitle: String,
)
