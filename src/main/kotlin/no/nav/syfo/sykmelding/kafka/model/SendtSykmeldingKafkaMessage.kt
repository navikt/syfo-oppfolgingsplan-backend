package no.nav.syfo.sykmelding.kafka.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDate

/**
 * Minimal DTO for teamsykmelding.syfo-sendt-sykmelding topic.
 * Only fields actually used by this consumer are modeled.
 * All classes use @JsonIgnoreProperties(ignoreUnknown = true) to safely ignore
 * any fields added by the producer without breaking deserialization.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SendtSykmeldingKafkaMessage(
    val sykmelding: ArbeidsgiverSykmelding,
    val kafkaMetadata: KafkaMetadata,
    val event: Event,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ArbeidsgiverSykmelding(
    val sykmeldingsperioder: List<SykmeldingsperiodeAGDTO>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SykmeldingsperiodeAGDTO(
    val fom: LocalDate,
    val tom: LocalDate,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Event(
    val arbeidsgiver: Arbeidsgiver? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Arbeidsgiver(
    val orgnummer: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KafkaMetadata(
    val fnr: String,
)
