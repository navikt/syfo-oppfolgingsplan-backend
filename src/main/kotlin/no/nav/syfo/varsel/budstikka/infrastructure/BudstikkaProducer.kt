package no.nav.syfo.varsel.budstikka.infrastructure

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.syfo.util.logger
import no.nav.syfo.varsel.budstikka.domain.DispatchHeader
import no.nav.syfo.varsel.budstikka.domain.dispatchJson
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

const val BUDSTIKKA_TOPIC = "team-esyfo.budstikka.v1"
private const val BUDSTIKKA_DISPATCH_TYPE = "BrukervarselCreate"
private const val BUDSTIKKA_SEND_TIMEOUT_MILLIS = 250L

class BudstikkaProducer(
    private val producer: KafkaProducer<String, String>,
    private val budstikkaOppfolgingsplanSykmeldtUrl: String,
) : BudstikkaPublisher {
    private val log = logger()

    override suspend fun publishOppfolgingsplanCreated(oppfolgingsplanUuid: UUID, sykmeldtFnr: String, eventId: UUID) : Unit =
        withContext(Dispatchers.IO) {
            val dispatch = createOppfolgingsplanCreatedDispatch(
                oppfolgingsplanUuid = oppfolgingsplanUuid,
                sykmeldtFnr = sykmeldtFnr,
                budstikkaOppfolgingsplanSykmeldtUrl = budstikkaOppfolgingsplanSykmeldtUrl,
            )
            val payload = dispatchJson.encodeToString(dispatch)
            val record = ProducerRecord(
                BUDSTIKKA_TOPIC,
                dispatch.content.partitionKey,
                payload,
            ).withHeader(DispatchHeader.EVENT_ID, eventId)

            log.info(
                "Publiserer Budstikka dispatch {}, {}, {}, {}",
                kv("topic", BUDSTIKKA_TOPIC),
                kv("type", BUDSTIKKA_DISPATCH_TYPE),
                kv("eventId", eventId),
                kv("oppfolgingsplanUuid", oppfolgingsplanUuid),
            )
            try {
                producer.send(record).get(BUDSTIKKA_SEND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                log.error(
                    "Publisering av Budstikka dispatch ble avbrutt {}, {}, {}, {}",
                    kv("topic", BUDSTIKKA_TOPIC),
                    kv("type", BUDSTIKKA_DISPATCH_TYPE),
                    kv("eventId", eventId),
                    kv("oppfolgingsplanUuid", oppfolgingsplanUuid),
                    e,
                )
                throw e
            } catch (e: TimeoutException) {
                log.error(
                    "Publisering av Budstikka dispatch timet ut {}, {}, {}, {}",
                    kv("topic", BUDSTIKKA_TOPIC),
                    kv("type", BUDSTIKKA_DISPATCH_TYPE),
                    kv("eventId", eventId),
                    kv("oppfolgingsplanUuid", oppfolgingsplanUuid),
                    e,
                )
                throw e
            } catch (e: Exception) {
                log.error(
                    "Feilet ved publisering av Budstikka dispatch til {}, {}, {}, {}",
                    kv("topic", BUDSTIKKA_TOPIC),
                    kv("type", BUDSTIKKA_DISPATCH_TYPE),
                    kv("eventId", eventId),
                    kv("oppfolgingsplanUuid", oppfolgingsplanUuid),
                    e,
                )
                throw e
            }
        }

    private fun <K, V> ProducerRecord<K, V>.withHeader(headerKey: String, headerValue: UUID) = apply {
        headers().add(
            headerKey,
            headerValue.toString().toByteArray(StandardCharsets.UTF_8),
        )
    }
}
