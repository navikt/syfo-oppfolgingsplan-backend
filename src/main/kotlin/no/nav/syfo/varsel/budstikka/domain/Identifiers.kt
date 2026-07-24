package no.nav.syfo.varsel.budstikka.domain

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class PersonIdentifier(
    val value: String,
) {
    override fun toString(): String = MASKED
}

object DispatchHeader {
    const val EVENT_ID = "eventId"
}

private const val MASKED = "***"
