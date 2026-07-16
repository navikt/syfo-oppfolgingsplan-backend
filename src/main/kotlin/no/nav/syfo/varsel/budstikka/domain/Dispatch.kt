package no.nav.syfo.varsel.budstikka.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class Dispatch(
    @Serializable(with = UuidSerializer::class)
    val eventId: UUID,
    val reference: String,
    val content: DispatchContent,
)

@Serializable
sealed interface DispatchContent {
    val partitionKey: String
}

val dispatchJson: Json = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
}
