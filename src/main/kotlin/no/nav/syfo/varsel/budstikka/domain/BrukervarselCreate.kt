package no.nav.syfo.varsel.budstikka.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
@SerialName("BrukervarselCreate")
data class BrukervarselCreate(
    val personIdentifier: PersonIdentifier,
    val varseltype: Varseltype,
    val text: String,
    val link: String? = null,
    @Serializable(with = InstantSerializer::class)
    val visibleUntil: Instant? = null,
    val externalVarsling: ExternalVarsling? = null,
    val brevFallback: BrevFallback? = null,
    val sendingWindow: SendingWindow? = null,
) : DispatchContent {
    override val partitionKey: String
        get() = personIdentifier.value
}
