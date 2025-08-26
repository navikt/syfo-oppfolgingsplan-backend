package no.nav.syfo.oppfolgingsplan.dto.formsnapshot

fun FormSnapshot.validateFields() {
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
                } else {
                    require(fieldSnapshot.options.count { it.wasSelected } <= 1) {
                        "At most one option can be selected for a non-required radio group field"
                    }
                }
            }
        }
    }
}
