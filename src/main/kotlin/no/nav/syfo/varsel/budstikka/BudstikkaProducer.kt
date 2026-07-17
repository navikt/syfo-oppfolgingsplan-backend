package no.nav.syfo.varsel.budstikka

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

    override fun publishOppfolgingsplanCreated(
        oppfolgingsplanUuid: UUID,
        sykmeldtFnr: String,
    ) {
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
        ).apply {
            headers().add(
                DispatchHeader.EVENT_ID,
                dispatch.eventId.toString().toByteArray(StandardCharsets.UTF_8),
            )
        }

        log.info(
            "Publiserer Budstikka shadow dispatch til topic={}, type={}, eventId={}",
            BUDSTIKKA_TOPIC,
            BUDSTIKKA_DISPATCH_TYPE,
            dispatch.eventId,
        )
        try {
            producer.send(record).get(BUDSTIKKA_SEND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            log.error(
                "Publisering av Budstikka shadow dispatch ble avbrutt til topic={}, type={}, eventId={}",
                BUDSTIKKA_TOPIC,
                BUDSTIKKA_DISPATCH_TYPE,
                dispatch.eventId,
                e,
            )
            throw e
        } catch (e: TimeoutException) {
            log.error(
                "Publisering av Budstikka shadow dispatch timet ut til topic={}, type={}, eventId={}",
                BUDSTIKKA_TOPIC,
                BUDSTIKKA_DISPATCH_TYPE,
                dispatch.eventId,
                e,
            )
            throw e
        } catch (e: Exception) {
            log.error(
                "Feilet ved publisering av Budstikka shadow dispatch til topic={}, type={}, eventId={}",
                BUDSTIKKA_TOPIC,
                BUDSTIKKA_DISPATCH_TYPE,
                dispatch.eventId,
                e,
            )
            throw e
        }
    }
}
