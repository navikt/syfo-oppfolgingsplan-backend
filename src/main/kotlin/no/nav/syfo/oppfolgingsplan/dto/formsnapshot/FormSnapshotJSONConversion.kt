package no.nav.syfo.oppfolgingsplan.dto.formsnapshot

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.util.configuredJacksonMapper
import no.nav.syfo.util.logger

private val logger = logger("no.nav.syfo.oppfolgingsplan.dto.FormSnapshotJSONConversion")

private val formSnapshotObjectMapper = configuredJacksonMapper

class FieldSnapshotDeserializer : JsonDeserializer<FieldSnapshot>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): FieldSnapshot {
        val node: JsonNode = p.codec.readTree(p)
        return when (val fieldType = node.get("fieldType").asText()) {
            FormSnapshotFieldType.TEXT.name -> ctxt.readTreeAsValue(
                node,
                TextFieldSnapshot::class.java
            )

            FormSnapshotFieldType.CHECKBOX_GROUP.name -> ctxt.readTreeAsValue(
                node,
                CheckboxGroupFieldSnapshot::class.java
            )

            FormSnapshotFieldType.RADIO_GROUP.name -> ctxt.readTreeAsValue(
                node,
                RadioGroupFieldSnapshot::class.java
            )

            FormSnapshotFieldType.CHECKBOX_SINGLE.name -> ctxt.readTreeAsValue(
                node,
                SingleCheckboxFieldSnapshot::class.java
            )

            FormSnapshotFieldType.DATE.name -> ctxt.readTreeAsValue(
                node,
                DateFieldSnapshot::class.java
            )

            else -> throw IllegalArgumentException("Unknown field type: $fieldType")
        }
    }
}

fun FormSnapshot.toJsonString(): String {
    return formSnapshotObjectMapper.writeValueAsString(this)
}

fun FormSnapshot.Companion.jsonToFormSnapshot(json: String): FormSnapshot {
    return try {
        formSnapshotObjectMapper.readValue(json)
    } catch (e: Exception) {
        logger.error("Failed to parse FormSnapshot JSON", e)
        throw e
    }
}
