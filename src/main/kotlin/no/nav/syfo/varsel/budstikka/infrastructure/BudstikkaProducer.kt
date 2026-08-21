package no.nav.syfo.varsel.budstikka.infrastructure

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.budstikka.contract.Budstikka
import no.nav.budstikka.contract.EncodedDispatch
import no.nav.budstikka.contract.EventId
import no.nav.budstikka.contract.Orgnummer
import no.nav.budstikka.contract.PersonIdentifier
import no.nav.budstikka.contract.SendingWindow
import no.nav.budstikka.contract.Varseltype
import no.nav.syfo.util.logger
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.apache.kafka.common.errors.TimeoutException as KafkaTimeoutException

private const val BUDSTIKKA_DISPATCH_TYPE = "BrukervarselCreate"
private const val BUDSTIKKA_SEND_TIMEOUT_MILLIS = 250L
const val OPPFOLGINGSPLAN_CREATED_BUDSTIKKA_TEXT = "Din arbeidsgiver har laget en oppfølgingsplan for deg"
const val PAAMINNELSE_BUDSTIKKA_TEXT = "Husk å lage en oppfølgingsplan sammen med den ansatte."

class BudstikkaProducer(
    private val producer: KafkaProducer<String, String>,
    private val budstikkaOppfolgingsplanSykmeldtUrl: String,
    private val budstikkaOppfolgingsplanNarmesteLederUrl: String,
) : BudstikkaPublisher {
    private val log = logger()

    override suspend fun publishOppfolgingsplanCreated(
        oppfolgingsplanUuid: UUID,
        sykmeldtFnr: String,
        eventId: UUID,
    ): Unit = withContext(Dispatchers.IO) {
        val dispatch = Budstikka.brukervarselCreate(
            eventId = EventId(eventId),
            reference = oppfolgingsplanUuid.toString(),
            sykmeldt = PersonIdentifier(sykmeldtFnr),
            varseltype = Varseltype.BESKJED,
            text = OPPFOLGINGSPLAN_CREATED_BUDSTIKKA_TEXT,
            link = budstikkaOppfolgingsplanSykmeldtUrl,
            sendingWindow = SendingWindow.BUDSTIKKA_OPENING_HOURS,
        )
        publish(dispatch, oppfolgingsplanUuid, eventId)
    }

    override suspend fun publishPaaminnelse(
        paaminnelseUuid: UUID,
        sykmeldtFnr: String,
        orgnummer: String,
        eventId: UUID,
        narmestelederId: String,
    ): Unit = withContext(Dispatchers.IO) {
//        val dispatch = Budstikka.arbeidsgivervarselCreate(
//            eventId = EventId(eventId),
//            reference = paaminnelseUuid.toString(),
//            orgnummer = Orgnummer(orgnummer),
//            recipient = Arbeidsgiver.NarmesteLeder(sykmeldt),
//            varseltype = Varseltype.BESKJED,
//            text = PAAMINNELSE_BUDSTIKKA_TEXT,
//            link = "$budstikkaOppfolgingsplanNarmesteLederUrl/$narmestelederId",
//            sendingWindow = SendingWindow.BUDSTIKKA_OPENING_HOURS,
//        )
//
//        publish(dispatch, paaminnelseUuid, eventId)

        log.info(
            "Sent paaminnelse with values:",
            kv("paaminnelseUuid", paaminnelseUuid.toString()),
            kv("sykmeldtFnr", sykmeldtFnr),
            kv("orgnummer", orgnummer),
            kv("eventId", eventId),
            kv("narmestelederId", narmestelederId),
            kv("link", "$budstikkaOppfolgingsplanNarmesteLederUrl/$narmestelederId"),
        )
    }

    private fun publish(
        dispatch: EncodedDispatch,
        referenceUuid: UUID,
        eventId: UUID,
    ) {
        val record = dispatch.toProducerRecord()
        log.info(
            "Publiserer Budstikka dispatch {}, {}, {}, {}",
            kv("topic", dispatch.topic),
            kv("type", BUDSTIKKA_DISPATCH_TYPE),
            kv("reference_uuid", referenceUuid),
            kv("event_id", eventId),
        )
        try {
            producer.send(record).get(BUDSTIKKA_SEND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            log.error(
                "Publisert til akkumulator, timeout på get. Ukjent utfall {}, {}, {}, {}",
                kv("topic", dispatch.topic),
                kv("type", BUDSTIKKA_DISPATCH_TYPE),
                kv("reference_uuid", referenceUuid),
                kv("event_id", eventId),
                e,
            )
            throw e
        } catch (e: KafkaTimeoutException) {
            log.error(
                "Publisering av Budstikka dispatch timet ut. Ikke levert {}, {}, {}, {}",
                kv("topic", dispatch.topic),
                kv("type", BUDSTIKKA_DISPATCH_TYPE),
                kv("reference_uuid", referenceUuid),
                kv("event_id", eventId),
                e,
            )
            throw e
        } catch (e: Exception) {
            log.error(
                "Feilet ved publisering av Budstikka dispatch til {}, {}, {}, {}",
                kv("topic", dispatch.topic),
                kv("type", BUDSTIKKA_DISPATCH_TYPE),
                kv("reference_uuid", referenceUuid),
                kv("event_id", eventId),
                e,
            )
            throw e
        }
    }

    private fun EncodedDispatch.toProducerRecord() = ProducerRecord(topic, key, value).apply {
        headerBytes().forEach { (name, value) ->
            headers().add(name, value)
        }
    }
}
