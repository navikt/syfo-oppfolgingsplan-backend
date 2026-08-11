package no.nav.syfo.varsel.budstikka.infrastructure

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.budstikka.contract.EncodedDispatch
import no.nav.syfo.util.logger
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.apache.kafka.common.errors.TimeoutException as KafkaTimeoutException

private const val BUDSTIKKA_SEND_TIMEOUT_MILLIS = 250L

class BudstikkaProducer(
    private val producer: KafkaProducer<String, String>,
) : BudstikkaPublisher {
    private val log = logger()

    override suspend fun publish(dispatch: EncodedDispatch): Unit = withContext(Dispatchers.IO) {
        val record = dispatch.toKafkaRecord()

        log.info(
            "Publishing Budstikka dispatch {}, {}",
            kv("topic", dispatch.topic),
            kv("eventId", dispatch.eventId),
        )
        try {
            producer.send(record).get(BUDSTIKKA_SEND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            log.error(
                "Timed out while waiting for Budstikka broker acknowledgement; outcome is unknown {}, {}",
                kv("topic", dispatch.topic),
                kv("eventId", dispatch.eventId),
                e,
            )
            throw e
        } catch (e: KafkaTimeoutException) {
            log.error(
                "Budstikka producer timed out before delivery {}, {}",
                kv("topic", dispatch.topic),
                kv("eventId", dispatch.eventId),
                e,
            )
            throw e
        } catch (e: Exception) {
            log.error(
                "Failed to publish Budstikka dispatch {}, {}",
                kv("topic", dispatch.topic),
                kv("eventId", dispatch.eventId),
                e,
            )
            throw e
        }
    }
}

internal fun EncodedDispatch.toKafkaRecord(): ProducerRecord<String, String> = ProducerRecord(topic, key, value).apply {
    headerBytes().forEach { (name, bytes) ->
        headers().add(name, bytes)
    }
}
