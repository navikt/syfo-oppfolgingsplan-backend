package no.nav.syfo.varsel

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime
import java.time.ZoneOffset
import no.nav.syfo.varsel.domain.ArbeidstakerHendelse
import no.nav.syfo.varsel.domain.EsyfovarselHendelse
import no.nav.syfo.varsel.domain.HendelseType
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.testcontainers.shaded.com.google.common.util.concurrent.SettableFuture

class EsyfovarselProducerTest : DescribeSpec({
    val kafkaProducerMock = mockk<KafkaProducer<String, EsyfovarselHendelse>>()
    val producer = EsyfovarselProducer(kafkaProducerMock)

    val arbeidstakerFnr = "12345678901"
    val orgnummer = "987654321"

    beforeTest {
        clearAllMocks()
    }
    describe("sendVarselToEsyfovarsel") {
        it("Calls send on Producer with ProducerRecord") {
            // Given
            val hendelse = createHendelse(orgnummer, arbeidstakerFnr)
            val recordMetadata = createRecordMetadata()

            val futureMock = mockk<SettableFuture<RecordMetadata>>()
            coEvery { futureMock.get() } returns recordMetadata
            coEvery { kafkaProducerMock.send(any<ProducerRecord<String, EsyfovarselHendelse>>()) } returns futureMock

            // When
            producer.sendVarselToEsyfovarsel(hendelse)

            // Then
            verify(exactly = 1) {
                kafkaProducerMock.send(withArg {
                    it.shouldBeInstanceOf<ProducerRecord<String, ArbeidstakerHendelse>>()
                    it.value().arbeidstakerFnr shouldBe arbeidstakerFnr
                    it.value().orgnummer shouldBe orgnummer
                    it.value().type shouldBe HendelseType.SM_OPPFOLGINGSPLAN_OPPRETTET
                })
            }
            verify(exactly = 1) { futureMock.get() }
        }
        it("Throws exception when producer.send fails") {
            // Given
            val hendelse = createHendelse(orgnummer, arbeidstakerFnr)
            val futureMock = mockk<SettableFuture<RecordMetadata>>()
            val forcedError = InterruptedException("Forced")
            coEvery { futureMock.get() } throws forcedError
            coEvery { kafkaProducerMock.send(any<ProducerRecord<String, EsyfovarselHendelse>>()) } returns futureMock

            // When
            val e = shouldThrow<Exception> {
                producer.sendVarselToEsyfovarsel(hendelse)
            }
            // Then
            e.message shouldContain forcedError.message!!
        }
    }
})

private fun createRecordMetadata(): RecordMetadata = RecordMetadata(
    TopicPartition("topic", 0),
    0L, // baseOffset
    1,
    LocalDateTime.now().toEpochSecond(ZoneOffset.UTC),
    5,
    10
)

private fun createHendelse(orgnummer: String, arbeidstakerFnr: String) = ArbeidstakerHendelse(
    HendelseType.SM_OPPFOLGINGSPLAN_OPPRETTET,
    ferdigstill = true,
    orgnummer = orgnummer,
    arbeidstakerFnr = arbeidstakerFnr,
    data = null
)
