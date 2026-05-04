package no.nav.syfo.sykmelding.kafka

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import no.nav.syfo.application.kafka.KafkaEnv
import no.nav.syfo.application.kafka.consumerProperties
import no.nav.syfo.sykmelding.db.SykmeldingsperiodeRepository
import no.nav.syfo.sykmelding.db.domain.SykmeldingsperiodeToStore
import no.nav.syfo.sykmelding.kafka.model.SendtSykmeldingKafkaMessage
import no.nav.syfo.util.configuredJacksonMapper
import no.nav.syfo.util.logger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.errors.WakeupException
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds

const val SYKMELDINGSPERIODE_TOPIC = "teamsykmelding.syfo-sendt-sykmelding"
const val SYKMELDINGSPERIODE_CONSUMER_GROUP = "syfo-oppfolgingsplan-backend-sykmeldingsperiode-v2"

class SykmeldingsperiodeConsumer(
    private val sykmeldingsperiodeRepository: SykmeldingsperiodeRepository,
    private val kafkaEnv: KafkaEnv,
    private val clock: Clock = Clock.system(ZONE_OSLO),
) {
    private val log = logger()

    @Volatile
    private var running = true

    @Volatile
    private var consumer: KafkaConsumer<String, String>? = null

    suspend fun runConsumer() = withContext(Dispatchers.IO) {
        var consecutiveFailures = 0

        while (currentCoroutineContext().isActive && running) {
            var kafkaConsumer: KafkaConsumer<String, String>? = null
            try {
                kafkaConsumer = KafkaConsumer(
                    consumerProperties(
                        env = kafkaEnv,
                        groupId = SYKMELDINGSPERIODE_CONSUMER_GROUP,
                    ),
                )
                consumer = kafkaConsumer
                kafkaConsumer.subscribe(listOf(SYKMELDINGSPERIODE_TOPIC))
                log.info("Subscribed to Kafka topic $SYKMELDINGSPERIODE_TOPIC")
                consecutiveFailures = 0

                while (currentCoroutineContext().isActive && running) {
                    val records = kafkaConsumer.poll(POLL_DURATION)
                    var deserializationErrors = 0

                    records.forEach { record ->
                        try {
                            processRecord(record)
                        } catch (ex: JsonProcessingException) {
                            deserializationErrors++
                            COUNT_SYKMELDING_DESERIALIZATION_ERROR.increment()
                            log.error(
                                "Failed to deserialize sykmelding at partition=${record.partition()}, offset=${record.offset()}",
                                ex,
                            )
                        }
                    }

                    if (deserializationErrors > 0 && deserializationErrors >= records.count()) {
                        error("All ${records.count()} records failed deserialization — likely a systematic DTO mismatch")
                    }

                    if (!records.isEmpty) {
                        kafkaConsumer.commitSync()
                    }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: WakeupException) {
                if (running && currentCoroutineContext().isActive) {
                    log.warn("Kafka consumer woke up unexpectedly, recreating consumer")
                }
            } catch (ex: Exception) {
                COUNT_SYKMELDING_RUNTIME_ERROR.increment()
                consecutiveFailures++
                val backoff = retryDelay(consecutiveFailures)
                log.error("Error while consuming sykmeldingsperioder from Kafka (attempt $consecutiveFailures, retrying in $backoff)", ex)
                delay(backoff)
            } finally {
                consumer = null
                kafkaConsumer?.close()
            }
        }
    }

    fun stop() {
        running = false
        consumer?.wakeup()
    }

    internal fun processRecord(
        record: ConsumerRecord<String, String>,
    ) {
        val recordValue = record.value()
        if (recordValue == null) {
            processTombstone(record)
            return
        }

        val kafkaMessage = configuredJacksonMapper.readValue<SendtSykmeldingKafkaMessage>(recordValue)

        val sykmeldingId = record.key()
        val organisasjonsnummer = kafkaMessage.event.arbeidsgiver?.orgnummer
        if (organisasjonsnummer == null) {
            log.warn(
                "Skipping sykmelding Kafka message without arbeidsgiver for sykmeldingId=$sykmeldingId",
            )
            return
        }

        val cutoffDate = LocalDate.now(clock).minusYears(2)
        val sykmeldingsperioderToStore = kafkaMessage.sykmelding.sykmeldingsperioder
            .filter { !it.tom.isBefore(cutoffDate) }
            .map { sykmeldingsperiode ->
                SykmeldingsperiodeToStore(
                    sykmeldtFnr = kafkaMessage.kafkaMetadata.fnr,
                    organisasjonsnummer = organisasjonsnummer,
                    sykmeldingId = sykmeldingId,
                    fom = sykmeldingsperiode.fom,
                    tom = sykmeldingsperiode.tom,
                )
            }

        if (sykmeldingsperioderToStore.isEmpty()) {
            log.debug("Skipping historical sykmeldingId=$sykmeldingId because all periods are older than retention")
            return
        }

        val insertedRows = sykmeldingsperiodeRepository.storeSykmeldingsperioder(sykmeldingsperioderToStore)
        if (insertedRows > 0) {
            COUNT_SYKMELDING_CONSUMED.increment(insertedRows.toDouble())
        }
    }

    internal fun processTombstone(
        record: ConsumerRecord<String, String>,
    ) {
        val sykmeldingId = record.key()
        if (sykmeldingId.isNullOrBlank()) {
            COUNT_SYKMELDING_DESERIALIZATION_ERROR.increment()
            log.warn(
                "Skipping sykmelding tombstone without key at topic=${record.topic()}, partition=${record.partition()}, offset=${record.offset()}",
            )
            return
        }

        sykmeldingsperiodeRepository.invalidateSykmelding(sykmeldingId)
        COUNT_SYKMELDING_TOMBSTONE.increment()
    }

    private companion object {
        val ZONE_OSLO: java.time.ZoneId = java.time.ZoneId.of("Europe/Oslo")
        val POLL_DURATION: Duration = Duration.ofSeconds(10)
        val MAX_RETRY_DELAY = 300.seconds
        val BASE_RETRY_DELAY = 10.seconds

        fun retryDelay(consecutiveFailures: Int): kotlin.time.Duration {
            val delaySeconds = BASE_RETRY_DELAY.inWholeSeconds * (1L shl (consecutiveFailures - 1).coerceAtMost(5))
            return delaySeconds.seconds.coerceAtMost(MAX_RETRY_DELAY)
        }
    }
}
