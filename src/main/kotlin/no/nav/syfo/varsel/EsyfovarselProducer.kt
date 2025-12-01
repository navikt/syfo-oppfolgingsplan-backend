package no.nav.syfo.varsel

import java.util.*
import no.nav.syfo.varsel.domain.EsyfovarselHendelse
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

class EsyfovarselProducer(private val producer: KafkaProducer<String, EsyfovarselHendelse>) {
    /**
     * Sends a varsel to the esyfovarsel topic synchronously.
     *
     * Note: Uses blocking .get() to ensure delivery confirmation before returning.
     * This is intentional since we want to know if the message was sent successfully.
     * The performance impact is acceptable given the low traffic volume of this application.
     */
    fun sendVarselToEsyfovarsel(esyfovarselHendelse: EsyfovarselHendelse) {
        log.info("Skal sende hendelse til varselbus topic ${esyfovarselHendelse.type}")
        try {
            producer.send(
                ProducerRecord(
                    ESYFOVARSEL_TOPIC,
                    UUID.randomUUID().toString(),
                    esyfovarselHendelse,
                )
            ).get()
        } catch (e: Exception) {
            log.error("Exception was thrown when attempting to send hendelse esyfovarsel: ${e.message}")
            throw e
        }
    }

    companion object {
        private const val ESYFOVARSEL_TOPIC = "team-esyfo.varselbus"
        private val log = LoggerFactory.getLogger(EsyfovarselProducer::class.java)
    }
}
