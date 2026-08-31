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
import no.nav.budstikka.contract.Arbeidsgivervarsel
import no.nav.budstikka.contract.Budstikka
import no.nav.budstikka.contract.EncodedDispatch
import no.nav.budstikka.contract.EventId
import no.nav.budstikka.contract.Oppgavetype
import no.nav.budstikka.contract.Orgnummer
import no.nav.budstikka.contract.PersonIdentifier
import no.nav.budstikka.contract.SendingWindow
import no.nav.budstikka.contract.Varseltype
import no.nav.syfo.varsel.budstikka.infrastructure.BudstikkaProducer
import no.nav.syfo.varsel.budstikka.infrastructure.EVALUERINGS_PAAMINNELSE_EMAIL_HTML
import no.nav.syfo.varsel.budstikka.infrastructure.EVALUERINGS_PAAMINNELSE_EMAIL_TITLE
import no.nav.syfo.varsel.budstikka.infrastructure.EVALUERINGS_PAAMINNELSE_TEXT
import no.nav.syfo.varsel.budstikka.infrastructure.OPPFOLGINGSPLAN_CREATED_BUDSTIKKA_TEXT
import no.nav.syfo.varsel.budstikka.infrastructure.OPPRETT_OPPFOLGINGSPLAN_PAAMINNELSE_BUDSTIKKA_TEXT
import no.nav.syfo.varsel.budstikka.infrastructure.OPPRETT_OPPFOLGINGSPLAN_PAAMINNELSE_EMAIL_HTML
import no.nav.syfo.varsel.budstikka.infrastructure.OPPRETT_OPPFOLGINGSPLAN_PAAMINNELSE_EMAIL_TITLE
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
        val dineSykmeldteOversiktUrl = "https://www.ekstern.dev.nav.no/syk/oppfolgingsplaner"
        val producer = BudstikkaProducer(
            kafkaProducerMock,
            budstikkaOppfolgingsplanSykmeldtUrl,
            dineSykmeldteOversiktUrl,
        )

        beforeTest {
            clearAllMocks(currentThreadOnly = true)
        }

        suspend fun assertDispatchIsSent(
            expectedDispatch: EncodedDispatch,
            publish: suspend () -> Unit,
        ) {
            val future = mockk<Future<RecordMetadata>>()
            every { future.get(250, TimeUnit.MILLISECONDS) } returns createRecordMetadata()
            every { kafkaProducerMock.send(any<ProducerRecord<String, String>>()) } returns future

            publish()

            verify(exactly = 1) {
                kafkaProducerMock.send(
                    withArg {
                        it.topic() shouldBe expectedDispatch.topic
                        it.key() shouldBe expectedDispatch.key
                        it.value() shouldBe expectedDispatch.value
                        it.headers().associate { header -> header.key() to header.value().toList() } shouldBe
                            expectedDispatch.headerBytes().mapValues { (_, value) -> value.toList() }
                    },
                )
            }
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
                    text = EVALUERINGS_PAAMINNELSE_TEXT,
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
                            it.value() shouldNotContain "\"link\""
                            actualHeaders shouldBe expectedHeaders
                        },
                    )
                }
                verify(exactly = 1) { future.get(250, TimeUnit.MILLISECONDS) }
            }
        }

        describe("publishMinSideArbeidsgiverEvalueringspaaminnelse") {
            it("publishes one employer notification with external email and no URL in the email") {
                val future = mockk<Future<RecordMetadata>>()
                val eventId = UUID.fromString("5fbc039e-b104-4554-809f-337d7ef804d0")
                val oppfolgingsplanUuid = UUID.fromString("0a5c80b8-2350-4f2a-b0e7-d1b796c6c8d4")
                val sykmeldtFnr = "00000000000"
                val organisasjonsnummer = "999999999"
                val expectedDispatch = Budstikka.arbeidsgivervarselCreate(
                    eventId = EventId(eventId),
                    reference = oppfolgingsplanUuid.toString(),
                    orgnummer = Orgnummer(organisasjonsnummer),
                    recipient = Arbeidsgivervarsel.NarmesteLeder(
                        sykmeldt = PersonIdentifier(sykmeldtFnr),
                    ),
                    htmlEmail = Arbeidsgivervarsel.HtmlEmailNotification(
                        emailTitle = EVALUERINGS_PAAMINNELSE_EMAIL_TITLE,
                        emailHtmlBody = EVALUERINGS_PAAMINNELSE_EMAIL_HTML,
                    ),
                    tag = "Oppfølging",
                    text = EVALUERINGS_PAAMINNELSE_TEXT,
                    link = dineSykmeldteOversiktUrl,
                    messageType = Arbeidsgivervarsel.MessageType.BESKJED,
                    sendingWindow = SendingWindow.BUDSTIKKA_OPENING_HOURS,
                )
                every { future.get(250, TimeUnit.MILLISECONDS) } returns createRecordMetadata()
                every { kafkaProducerMock.send(any<ProducerRecord<String, String>>()) } returns future

                producer.publishMinSideArbeidsgiverEvalueringspaaminnelse(
                    oppfolgingsplanUuid = oppfolgingsplanUuid,
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnummer = organisasjonsnummer,
                    eventId = eventId,
                )

                EVALUERINGS_PAAMINNELSE_EMAIL_HTML shouldNotContain "https://"
                EVALUERINGS_PAAMINNELSE_EMAIL_HTML shouldNotContain "http://"
                verify(exactly = 1) {
                    kafkaProducerMock.send(
                        withArg {
                            it.topic() shouldBe expectedDispatch.topic
                            it.key() shouldBe expectedDispatch.key
                            it.value() shouldBe expectedDispatch.value
                            it.value() shouldContain "\"emailBodyFormat\":\"HTML\""
                            it.headers().associate { header -> header.key() to header.value().toList() } shouldBe
                                expectedDispatch.headerBytes().mapValues { (_, value) -> value.toList() }
                        },
                    )
                }
                verify(exactly = 1) { future.get(250, TimeUnit.MILLISECONDS) }
            }
        }

        describe("publishOpprettOppfolgingsplanPaaminnelse") {
            val eventId = UUID.fromString("5fbc039e-b104-4554-809f-337d7ef804d0")
            val bestillingId =
                UUID.fromString("0a5c80b8-2350-4f2a-b0e7-d1b796c6c8d4")
            val narmestelederId = UUID.fromString("9e629cad-a60c-464c-98ef-f30dd33f2da6")
            val sykmeldtFnr = "12345678901"
            val orgnummer = "123456789"

            it("sends an employer notification to the narmeste leder") {
                val expectedDispatch = Budstikka.arbeidsgivervarselCreate(
                    eventId = EventId(eventId),
                    reference = bestillingId.toString(),
                    orgnummer = Orgnummer(orgnummer),
                    recipient = Arbeidsgivervarsel.NarmesteLeder(
                        sykmeldt = PersonIdentifier(sykmeldtFnr),
                    ),
                    htmlEmail = Arbeidsgivervarsel.HtmlEmailNotification(
                        emailTitle = OPPRETT_OPPFOLGINGSPLAN_PAAMINNELSE_EMAIL_TITLE,
                        emailHtmlBody = OPPRETT_OPPFOLGINGSPLAN_PAAMINNELSE_EMAIL_HTML,
                    ),
                    tag = "Oppfølging",
                    text = OPPRETT_OPPFOLGINGSPLAN_PAAMINNELSE_BUDSTIKKA_TEXT,
                    link = "$dineSykmeldteOversiktUrl/$narmestelederId",
                    messageType = Arbeidsgivervarsel.MessageType.BESKJED,
                    sendingWindow = SendingWindow.BUDSTIKKA_OPENING_HOURS,
                )

                assertDispatchIsSent(expectedDispatch) {
                    producer.publishOpprettOppfolgingsplanPaaminnelse(
                        bestillingId = bestillingId,
                        sykmeldtFnr = sykmeldtFnr,
                        orgnummer = orgnummer,
                        eventId = eventId,
                        narmestelederId = narmestelederId,
                    )
                }

                OPPRETT_OPPFOLGINGSPLAN_PAAMINNELSE_EMAIL_HTML shouldContain
                    "Du har en ansatt som er sykmeldt hvor fristen for å lage en oppfølgingsplan nærmer seg."
                expectedDispatch.value shouldContain "\"emailBodyFormat\":\"HTML\""
                expectedDispatch.value shouldContain "Har du spørsmål? Ring oss på 55 55 33 36."
            }

            it("uses the approved email content and evaluation email presentation") {
                val approvedContent = listOf(
                    "Hei,",
                    "Du har en ansatt som er sykmeldt hvor fristen for å lage en oppfølgingsplan nærmer seg.",
                    "Du trenger ikke ha alle svarene klare. Avtal en prat hvor dere sammen finner ut om noen arbeidsoppgaver er mulig å gjøre i sykmeldingsperioden.",
                    "Jo tidligere dere gjør dette desto lettere er det for mange å komme tilbake i jobb og at langvarig fravær forebygges.",
                    "Slik gjør du det:",
                    "Logg inn på Min side – arbeidsgiver.",
                    "🔔 Klikk på bjella. Der finner du meldingen om å lage oppfølgingsplan (evt. meld fra at det ikke er behov nå).",
                    "Har du spørsmål? Ring oss på 55 55 33 36.",
                    "Du kan ikke svare på denne meldingen.",
                    "Vennlig hilsen Nav",
                )
                val sharedVisualMarkers = listOf(
                    """<table role="presentation" width="100%"""",
                    "max-width: 640px",
                    "border-radius: 8px",
                    "font-family: Arial, sans-serif",
                    "background-color: #004367",
                    "&#9993;&#65039;",
                    "padding: 32px; font-size: 18px; line-height: 1.5",
                    "border-top: 1px solid #d8d8d8",
                )

                approvedContent.forEach {
                    OPPRETT_OPPFOLGINGSPLAN_PAAMINNELSE_EMAIL_HTML shouldContain it
                }
                sharedVisualMarkers.forEach {
                    EVALUERINGS_PAAMINNELSE_EMAIL_HTML shouldContain it
                    OPPRETT_OPPFOLGINGSPLAN_PAAMINNELSE_EMAIL_HTML shouldContain it
                }
                OPPRETT_OPPFOLGINGSPLAN_PAAMINNELSE_EMAIL_HTML shouldContain "<ol "
                OPPRETT_OPPFOLGINGSPLAN_PAAMINNELSE_EMAIL_HTML shouldContain "<li "
            }

            it("sends a Dine Sykmeldte notification") {
                val expectedDispatch = Budstikka.dineSykmeldteVarselCreate(
                    eventId = EventId(eventId),
                    reference = bestillingId.toString(),
                    sykmeldt = PersonIdentifier(sykmeldtFnr),
                    orgnummer = Orgnummer(orgnummer),
                    oppgavetype = Oppgavetype.OPPFOLGINGSPLAN_PAAMINNELSE,
                    text = OPPRETT_OPPFOLGINGSPLAN_PAAMINNELSE_BUDSTIKKA_TEXT,
                    sendingWindow = SendingWindow.ONGOING,
                )

                assertDispatchIsSent(expectedDispatch) {
                    producer.publishOpprettOppfolgingsplanPaaminnelseToDineSykmeldte(
                        bestillingId = bestillingId,
                        sykmeldtFnr = sykmeldtFnr,
                        orgnummer = orgnummer,
                        eventId = eventId,
                    )
                }
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
