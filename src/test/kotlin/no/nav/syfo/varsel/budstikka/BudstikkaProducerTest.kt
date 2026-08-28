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
import no.nav.budstikka.contract.Oppgavetype
import no.nav.budstikka.contract.Orgnummer
import no.nav.budstikka.contract.PersonIdentifier
import no.nav.budstikka.contract.SendingWindow
import no.nav.budstikka.contract.Varseltype
import no.nav.syfo.varsel.budstikka.infrastructure.BudstikkaProducer
import no.nav.syfo.varsel.budstikka.infrastructure.DINE_SYKMELDTE_PAAMINNELSE_TEXT
import no.nav.syfo.varsel.budstikka.infrastructure.OPPFOLGINGSPLAN_CREATED_BUDSTIKKA_TEXT
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
        val producer = BudstikkaProducer(
            kafkaProducerMock,
            budstikkaOppfolgingsplanSykmeldtUrl,
        )

        beforeTest {
            clearAllMocks(currentThreadOnly = true)
        }

        describe("publishOppfolgingsplanCreated") {
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
                    sendingWindow = SendingWindow.BUDSTIKKA_OPENING_HOURS,
                )
                every { future.get(250, TimeUnit.MILLISECONDS) } returns createRecordMetadata()
                every { kafkaProducerMock.send(any<ProducerRecord<String, String>>()) } returns future

                producer.publishOppfolgingsplanCreated(
                    oppfolgingsplanUuid = oppfolgingsplanUuid,
                    sykmeldtFnr = sykmeldtFnr,
                    eventId = eventId,
                )

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
                            it.value() shouldContain "\"sendingWindow\":\"BUDSTIKKA_OPENING_HOURS\""
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

                val error = shouldThrow<Exception> {
                    producer.publishOppfolgingsplanCreated(
                        oppfolgingsplanUuid = UUID.fromString("0a5c80b8-2350-4f2a-b0e7-d1b796c6c8d4"),
                        sykmeldtFnr = "12345678901",
                        eventId = eventId,
                    )
                }

                error.message shouldContain "Forced"
                verify(exactly = 1) { failedFuture.get(250, TimeUnit.MILLISECONDS) }
            }
        }

        describe("publishDineSykmeldteEvalueringspaaminnelse") {
            it("publishes the agreed activity through the Dine Sykmeldte facade") {
                val future = mockk<Future<RecordMetadata>>()
                val eventId = UUID.fromString("5fbc039e-b104-4554-809f-337d7ef804d0")
                val oppfolgingsplanUuid = UUID.fromString("0a5c80b8-2350-4f2a-b0e7-d1b796c6c8d4")
                val sykmeldtFnr = "00000000000"
                val organisasjonsnummer = "999999999"
                val expectedDispatch = Budstikka.dineSykmeldteVarselCreate(
                    eventId = EventId(eventId),
                    reference = oppfolgingsplanUuid.toString(),
                    sykmeldt = PersonIdentifier(sykmeldtFnr),
                    orgnummer = Orgnummer(organisasjonsnummer),
                    oppgavetype = Oppgavetype.OPPFOLGINGSPLAN_PAAMINNELSE,
                    text = DINE_SYKMELDTE_PAAMINNELSE_TEXT,
                    sendingWindow = SendingWindow.ONGOING,
                )
                every { future.get(250, TimeUnit.MILLISECONDS) } returns createRecordMetadata()
                every { kafkaProducerMock.send(any<ProducerRecord<String, String>>()) } returns future

                producer.publishDineSykmeldteEvalueringspaaminnelse(
                    oppfolgingsplanUuid = oppfolgingsplanUuid,
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnummer = organisasjonsnummer,
                    eventId = eventId,
                )

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
                            it.value() shouldContain "\"sendingWindow\":\"ONGOING\""
                            it.value() shouldContain "\"link\":null"
                            actualHeaders shouldBe expectedHeaders
                        },
                    )
                }
                verify(exactly = 1) { future.get(250, TimeUnit.MILLISECONDS) }
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
