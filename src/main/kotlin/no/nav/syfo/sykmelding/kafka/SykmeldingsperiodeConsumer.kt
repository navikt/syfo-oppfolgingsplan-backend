package no.nav.syfo.sykmelding.kafka

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.module.kotlin.readValue
import io.micrometer.core.instrument.Counter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import no.nav.syfo.application.kafka.KafkaEnv
import no.nav.syfo.application.kafka.consumerProperties
import no.nav.syfo.application.metric.METRICS_REGISTRY
import no.nav.syfo.sykmelding.db.SykmeldingsperiodeRepository
import no.nav.syfo.sykmelding.db.SykmeldingsperiodeToStore
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
const val SYKMELDINGSPERIODE_CONSUMER_GROUP = "syfo-oppfolgingsplan-backend-sykmeldingsperiode"
const val SYKMELDING_CONSUMED_TOTAL = "syfo_oppfolgingsplan_sykmelding_consumed_total"
const val SYKMELDING_TOMBSTONE_TOTAL = "syfo_oppfolgingsplan_sykmelding_tombstone_total"
const val SYKMELDING_ERROR_TOTAL = "syfo_oppfolgingsplan_sykmelding_error_total"

val COUNT_SYKMELDING_CONSUMED: Counter = Counter.builder(SYKMELDING_CONSUMED_TOTAL)
    .description("Counts the number of sykmeldingsperioder stored from Kafka")
    .register(METRICS_REGISTRY)
val COUNT_SYKMELDING_TOMBSTONE: Counter = Counter.builder(SYKMELDING_TOMBSTONE_TOTAL)
    .description("Counts the number of sykmelding tombstones processed from Kafka")
    .register(METRICS_REGISTRY)
val COUNT_SYKMELDING_ERROR: Counter = Counter.builder(SYKMELDING_ERROR_TOTAL)
    .description("Counts the number of sykmelding Kafka consumer errors")
    .register(METRICS_REGISTRY)

class SykmeldingsperiodeConsumer(
    private val sykmeldingsperiodeRepository: SykmeldingsperiodeRepository,
    private val kafkaEnv: KafkaEnv,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = logger()

    @Volatile
    private var running = true

    @Volatile
    private var consumer: KafkaConsumer<String, String>? = null

    suspend fun runConsumer() = withContext(Dispatchers.IO) {
        while (currentCoroutineContext().isActive && running) {
            val kafkaConsumer = KafkaConsumer<String, String>(
                consumerProperties(
                    env = kafkaEnv,
                    groupId = SYKMELDINGSPERIODE_CONSUMER_GROUP,
                ),
            )
            consumer = kafkaConsumer

            try {
                kafkaConsumer.subscribe(listOf(SYKMELDINGSPERIODE_TOPIC))
                log.info("Subscribed to Kafka topic $SYKMELDINGSPERIODE_TOPIC")

                while (currentCoroutineContext().isActive && running) {
                    val records = kafkaConsumer.poll(POLL_DURATION)
                    records.forEach { processRecord(it) }

                    if (!records.isEmpty) {
                        kafkaConsumer.commitSync()
                    }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: WakeupException) {
                if (running && currentCoroutineContext().isActive) {
                    log.warn("Kafka consumer woke up unexpectedly, recreating consumer")
                    delay(RETRY_DELAY)
                }
            } catch (ex: Exception) {
                COUNT_SYKMELDING_ERROR.increment()
                log.error("Error while consuming sykmeldingsperioder from Kafka, recreating consumer", ex)
                delay(RETRY_DELAY)
            } finally {
                consumer = null
                kafkaConsumer.close()
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

        val kafkaMessage = try {
            configuredJacksonMapper.readValue<SendtSykmeldingKafkaMessage>(recordValue)
        } catch (ex: JsonProcessingException) {
            COUNT_SYKMELDING_ERROR.increment()
            log.error(
                "Failed to deserialize sykmelding Kafka message at topic=${record.topic()}, partition=${record.partition()}, offset=${record.offset()}",
                ex,
            )
            return
        }

        val organisasjonsnummer = kafkaMessage.event.arbeidsgiver?.orgnummer
        if (organisasjonsnummer == null) {
            COUNT_SYKMELDING_ERROR.increment()
            log.warn(
                "Skipping sykmelding Kafka message without arbeidsgiver for sykmeldingId=${kafkaMessage.event.sykmeldingId}",
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
                    sykmeldingId = kafkaMessage.event.sykmeldingId,
                    fom = sykmeldingsperiode.fom,
                    tom = sykmeldingsperiode.tom,
                )
            }

        if (sykmeldingsperioderToStore.isEmpty()) {
            log.debug("Skipping historical sykmeldingId=${kafkaMessage.event.sykmeldingId} because all periods are older than retention")
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
            COUNT_SYKMELDING_ERROR.increment()
            log.warn(
                "Skipping sykmelding tombstone without key at topic=${record.topic()}, partition=${record.partition()}, offset=${record.offset()}",
            )
            return
        }

        sykmeldingsperiodeRepository.invalidateSykmelding(sykmeldingId)
        COUNT_SYKMELDING_TOMBSTONE.increment()
    }

    private companion object {
        val POLL_DURATION: Duration = Duration.ofSeconds(10)
        val RETRY_DELAY = 10.seconds
    }
}
