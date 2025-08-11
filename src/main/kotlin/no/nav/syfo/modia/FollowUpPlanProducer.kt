package no.nav.syfo.modia

import java.util.UUID
import no.nav.syfo.modia.domain.KFollowUpPlan
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

class FollowUpPlanProducer(private val producer: KafkaProducer<String, KFollowUpPlan>) {

    fun createFollowUpPlanTaskInModia(kFollowUpPlan: KFollowUpPlan) {
        try {
            producer.send(
                ProducerRecord(
                    MODIA_FOLLOWUP_PLAN_TOPIC,
                    UUID.randomUUID().toString(),
                    kFollowUpPlan,
                )
            ).get()
            log.info("Followup-plan task sent to Modia with UUID ${kFollowUpPlan.uuid}")
        } catch (e: Exception) {
            log.error("Exception was thrown when attempting to send FollowUpPlan: ${e.message}")
            throw e
        }
    }

    companion object {
        private const val MODIA_FOLLOWUP_PLAN_TOPIC = "team-esyfo.aapen-syfo-oppfolgingsplan-lps-nav-v2"
        private val log = LoggerFactory.getLogger(FollowUpPlanProducer::class.java)
    }
}
