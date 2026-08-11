package no.nav.syfo.varsel.budstikka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.budstikka.contract.Budstikka
import no.nav.budstikka.contract.EventId
import no.nav.budstikka.contract.PersonIdentifier
import no.nav.budstikka.contract.Varseltype
import no.nav.syfo.oppfolgingsplan.outbox.OPPFOLGINGSPLAN_CREATED_BUDSTIKKA_TEXT
import no.nav.syfo.varsel.budstikka.infrastructure.BudstikkaProducer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
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
        val producer = BudstikkaProducer(kafkaProducerMock)

        beforeTest {
            clearAllMocks(currentThreadOnly = true)
        }

        describe("publish") {
            it("sends ProducerRecord with topic, key, header and serialized dispatch") {
                val future = mockk<Future<RecordMetadata>>()
                val eventId = UUID.fromString("5fbc039e-b104-4554-809f-337d7ef804d0")
                val oppfolgingsplanUuid = UUID.fromString("0a5c80b8-2350-4f2a-b0e7-d1b796c6c8d4")
                val sykmeldtFnr = "12345678901"
                val expectedDispatch = Budstikka.brukervarselCreate(
                    eventId = EventId(eventId),
                    reference = oppfolgingsplanUuid.toString(),
                    sykmeldt = PersonIdentifier(sykmeldtFnr),
                    varseltype = Varseltype.BESKJED,
                    text = OPPFOLGINGSPLAN_CREATED_BUDSTIKKA_TEXT,
                    link = budstikkaOppfolgingsplanSykmeldtUrl,
                )
                every { future.get(250, TimeUnit.MILLISECONDS) } returns createRecordMetadata()
                every { kafkaProducerMock.send(any<ProducerRecord<String, String>>()) } returns future

                producer.publish(expectedDispatch)

                verify(exactly = 1) {
                    kafkaProducerMock.send(
                        withArg {
                            val actualHeaders = it.headers().associate { header ->
                                header.key() to header.value().toList()
                            }
                            val expectedHeaders = expectedDispatch.headerBytes().mapValues { (_, value) ->
                                value.toList()
                            }
                            it.topic() shouldBe expectedDispatch.topic
                            it.key() shouldBe expectedDispatch.key
                            it.value() shouldBe expectedDispatch.value
                            actualHeaders shouldBe expectedHeaders
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
                val dispatch = Budstikka.brukervarselCreate(
                    eventId = EventId(eventId),
                    reference = "0a5c80b8-2350-4f2a-b0e7-d1b796c6c8d4",
                    sykmeldt = PersonIdentifier("12345678901"),
                    varseltype = Varseltype.BESKJED,
                    text = OPPFOLGINGSPLAN_CREATED_BUDSTIKKA_TEXT,
                    link = budstikkaOppfolgingsplanSykmeldtUrl,
                )

                val error = shouldThrow<Exception> {
                    producer.publish(dispatch)
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
