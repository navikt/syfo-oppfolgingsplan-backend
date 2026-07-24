package no.nav.syfo.varsel.budstikka.domain

import kotlinx.serialization.Serializable

@Serializable
enum class Varseltype {
    BESKJED,
    OPPGAVE,
}

@Serializable
enum class ExternalChannel {
    SMS,
    EMAIL,
}

@Serializable
data class ExternalVarsling(
    val channels: Set<ExternalChannel> = setOf(ExternalChannel.SMS, ExternalChannel.EMAIL),
    val smsText: String? = null,
    val emailTitle: String? = null,
    val emailText: String? = null,
)

@Serializable
enum class DistributionType {
    IMPORTANT,
    OTHER,
}

@Serializable
data class BrevFallback(
    val journalpostId: String,
    val distributionType: DistributionType = DistributionType.IMPORTANT,
)

@Serializable
enum class SendingWindow {
    ONGOING,
    NKS_OPENING_HOURS,
}
