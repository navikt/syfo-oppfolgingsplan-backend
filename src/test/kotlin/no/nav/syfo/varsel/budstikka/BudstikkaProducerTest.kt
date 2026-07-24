package no.nav.syfo.varsel.budstikka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.varsel.budstikka.domain.DispatchHeader
import no.nav.syfo.varsel.budstikka.infrastructure.BUDSTIKKA_TOPIC
import no.nav.syfo.varsel.budstikka.infrastructure.BudstikkaProducer
import no.nav.syfo.varsel.budstikka.infrastructure.OPPFOLGINGSPLAN_CREATED_BUDSTIKKA_TEXT
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class BudstikkaProducerTest :
    DescribeSpec({
        val kafkaProducerMock = mockk<KafkaProducer<String, String>>()
        val budstikkaOppfolgingsplanSykmeldtUrl = "https://www.ekstern.dev.nav.no/syk/oppfolgingsplan/sykmeldt"
        val producer = BudstikkaProducer(kafkaProducerMock, budstikkaOppfolgingsplanSykmeldtUrl)

        beforeTest {
            clearAllMocks(currentThreadOnly = true)
        }

        describe("publishOppfolgingsplanCreated") {
            it("sends ProducerRecord with topic, key, header and serialized dispatch") {
                val future = mockk<Future<RecordMetadata>>()
                val eventId = UUID.fromString("5fbc039e-b104-4554-809f-337d7ef804d0")
                every { future.get(250, TimeUnit.MILLISECONDS) } returns createRecordMetadata()
                every { kafkaProducerMock.send(any<ProducerRecord<String, String>>()) } returns future

                producer.publishOppfolgingsplanCreated(
                    oppfolgingsplanUuid = java.util.UUID.fromString("0a5c80b8-2350-4f2a-b0e7-d1b796c6c8d4"),
                    sykmeldtFnr = "12345678901",
                    eventId = eventId,
                ) shouldBe eventId

                verify(exactly = 1) {
                    kafkaProducerMock.send(
                        withArg {
                            val eventIdHeader = it.headers().lastHeader(DispatchHeader.EVENT_ID).value().toString(StandardCharsets.UTF_8)
                            it.topic() shouldBe BUDSTIKKA_TOPIC
                            it.key() shouldBe "12345678901"
                            eventIdHeader shouldBe eventId.toString()
                            it.value() shouldContain "\"type\":\"BrukervarselCreate\""
                            it.value() shouldNotContain "\"eventId\""
                            it.value() shouldContain "\"reference\":\"0a5c80b8-2350-4f2a-b0e7-d1b796c6c8d4\""
                            it.value() shouldContain "\"personIdentifier\":\"12345678901\""
                            it.value() shouldContain "\"varseltype\":\"BESKJED\""
                            it.value() shouldContain "\"text\":\"${OPPFOLGINGSPLAN_CREATED_BUDSTIKKA_TEXT}\""
                            it.value() shouldContain "\"link\":\"$budstikkaOppfolgingsplanSykmeldtUrl\""
                        },
                    )
                }
                verify(exactly = 1) { future.get(250, TimeUnit.MILLISECONDS) }
            }

            it("rethrows exception when send confirmation times out") {
                val failedFuture = mockk<Future<RecordMetadata>>()
                val eventId = UUID.fromString("5fbc039e-b104-4554-809f-337d7ef804d0")
                every { failedFuture.get(250, TimeUnit.MILLISECONDS) } throws TimeoutException("Forced")
                every { kafkaProducerMock.send(any<ProducerRecord<String, String>>()) } returns failedFuture

                val error = shouldThrow<Exception> {
                    producer.publishOppfolgingsplanCreated(
                        oppfolgingsplanUuid = java.util.UUID.fromString("0a5c80b8-2350-4f2a-b0e7-d1b796c6c8d4"),
                        sykmeldtFnr = "12345678901",
                        eventId = eventId,
                    )
                }

                error.message shouldContain "Forced"
                verify(exactly = 1) { failedFuture.get(250, TimeUnit.MILLISECONDS) }
            }
        }
    })

private fun createRecordMetadata(): RecordMetadata = RecordMetadata(
    TopicPartition("topic", 0),
    0L,
    1,
    LocalDateTime.now().toEpochSecond(ZoneOffset.UTC),
    5,
    10,
)
