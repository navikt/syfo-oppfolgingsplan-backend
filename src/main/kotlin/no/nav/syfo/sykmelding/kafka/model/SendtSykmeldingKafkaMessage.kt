package no.nav.syfo.sykmelding.kafka.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDate
import java.time.OffsetDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class SendtSykmeldingKafkaMessage(
    val sykmelding: ArbeidsgiverSykmelding,
    val kafkaMetadata: KafkaMetadata,
    val event: Event,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ArbeidsgiverSykmelding(
    val sykmeldingsperioder: List<SykmeldingsperiodeAGDTO>,
    val syketilfelleStartDato: LocalDate?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SykmeldingsperiodeAGDTO(
    val fom: LocalDate,
    val tom: LocalDate,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Event(
    val sykmeldingId: String,
    val timestamp: OffsetDateTime,
    val arbeidsgiver: Arbeidsgiver? = null,
    val brukerSvar: BrukerSvar? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Arbeidsgiver(
    val orgnummer: String,
    val juridiskOrgnummer: String? = null,
    val orgNavn: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BrukerSvar(
    val erOpplysningeneRiktige: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KafkaMetadata(
    val sykmeldingId: String,
    val timestamp: OffsetDateTime,
    val fnr: String,
    val source: String,
)
