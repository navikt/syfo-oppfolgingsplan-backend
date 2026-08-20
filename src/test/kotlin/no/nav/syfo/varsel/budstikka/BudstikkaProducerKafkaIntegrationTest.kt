package no.nav.syfo.varsel.budstikka

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import no.nav.budstikka.contract.Budstikka
import no.nav.budstikka.contract.EventId
import no.nav.budstikka.contract.PersonIdentifier
import no.nav.budstikka.contract.SendingWindow
import no.nav.budstikka.contract.Varseltype
import no.nav.syfo.varsel.budstikka.infrastructure.BudstikkaProducer
import no.nav.syfo.varsel.budstikka.infrastructure.OPPFOLGINGSPLAN_CREATED_BUDSTIKKA_TEXT
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.testcontainers.kafka.KafkaContainer
import java.time.Duration
import java.util.Properties
import java.util.UUID

class BudstikkaProducerKafkaIntegrationTest :
    FunSpec({
        val kafka = KafkaContainer("apache/kafka-native:3.8.0")
        val oppfolgingsplanUuid = UUID.fromString("0a5c80b8-2350-4f2a-b0e7-d1b796c6c8d4")
        val eventId = UUID.fromString("5fbc039e-b104-4554-809f-337d7ef804d0")
        val sykmeldtFnr = "12345678901"
        val oppfolgingsplanUrl = "https://www.ekstern.dev.nav.no/syk/oppfolgingsplan/sykmeldt"

        beforeSpec {
            kafka.start()
        }
        afterSpec {
            kafka.stop()
        }

        test("BudstikkaProducer delivers the contract-encoded record to Kafka") {
            val expectedDispatch = Budstikka.brukervarselCreate(
                eventId = EventId(eventId),
                reference = oppfolgingsplanUuid.toString(),
                sykmeldt = PersonIdentifier(sykmeldtFnr),
                varseltype = Varseltype.BESKJED,
                text = OPPFOLGINGSPLAN_CREATED_BUDSTIKKA_TEXT,
                link = oppfolgingsplanUrl,
                sendingWindow = SendingWindow.BUDSTIKKA_OPENING_HOURS,
            )

            KafkaConsumer<String, String>(consumerProperties(kafka.bootstrapServers)).use { consumer ->
                KafkaProducer<String, String>(producerProperties(kafka.bootstrapServers)).use { kafkaProducer ->
                    consumer.subscribe(listOf(Budstikka.TOPIC))
                    consumer.poll(Duration.ofMillis(100))

                    runBlocking {
                        BudstikkaProducer(
                            kafkaProducer,
                            oppfolgingsplanUrl,
                            "https://www.ekstern.dev.nav.no/syk/oppfolgingsplan/arbeidsgiver",
                        ).publishOppfolgingsplanCreated(
                            oppfolgingsplanUuid = oppfolgingsplanUuid,
                            sykmeldtFnr = sykmeldtFnr,
                            eventId = eventId,
                        )
                    }

                    val record = consumer.pollSingleRecord()
                    record.topic() shouldBe expectedDispatch.topic
                    record.key() shouldBe expectedDispatch.key
                    record.value() shouldBe expectedDispatch.value
                    record.value() shouldContain "\"sendingWindow\":\"BUDSTIKKA_OPENING_HOURS\""
                    record.headers().associate { header ->
                        header.key() to header.value().toList()
                    } shouldBe expectedDispatch.headerBytes().mapValues { (_, value) ->
                        value.toList()
                    }
                }
            }
        }
    })

private fun consumerProperties(bootstrapServers: String): Properties = Properties().apply {
    put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
    put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString())
    put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
    put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
}

private fun producerProperties(bootstrapServers: String): Properties = Properties().apply {
    put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
    put(ProducerConfig.ACKS_CONFIG, "all")
    put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
    put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
}

private fun KafkaConsumer<String, String>.pollSingleRecord(): ConsumerRecord<String, String> {
    val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
    while (System.nanoTime() < deadline) {
        val records = poll(Duration.ofMillis(250))
        if (!records.isEmpty) {
            records.count() shouldBe 1
            return records.iterator().next()
        }
    }
    error("Timed out waiting for Budstikka record")
}
