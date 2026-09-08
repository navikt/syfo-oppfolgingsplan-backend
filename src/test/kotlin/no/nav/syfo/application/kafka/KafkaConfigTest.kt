package no.nav.syfo.application.kafka

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.apache.kafka.clients.producer.ProducerConfig

class KafkaConfigTest :
    FunSpec({
        test("Budstikka producer waits for Kafka's bounded delivery result") {
            val properties = budstikkaProducerProperties(KafkaEnv.createForLocal())

            properties[ProducerConfig.MAX_BLOCK_MS_CONFIG] shouldBe BUDSTIKKA_MAX_BLOCK_MILLIS
            properties[ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG] shouldBe BUDSTIKKA_REQUEST_TIMEOUT_MILLIS
            properties[ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG] shouldBe BUDSTIKKA_DELIVERY_TIMEOUT_MILLIS
            properties[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] shouldBe true
            (BUDSTIKKA_DELIVERY_TIMEOUT_MILLIS > BUDSTIKKA_REQUEST_TIMEOUT_MILLIS) shouldBe true
            (BUDSTIKKA_SEND_TIMEOUT_MILLIS > BUDSTIKKA_DELIVERY_TIMEOUT_MILLIS) shouldBe true
        }
    })
