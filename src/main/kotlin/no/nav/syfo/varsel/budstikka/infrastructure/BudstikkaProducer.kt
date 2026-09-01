package no.nav.syfo.varsel.budstikka.infrastructure

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.logstash.logback.argument.StructuredArgument
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.budstikka.contract.Arbeidsgivervarsel
import no.nav.budstikka.contract.Budstikka
import no.nav.budstikka.contract.EncodedDispatch
import no.nav.budstikka.contract.EventId
import no.nav.budstikka.contract.Oppgavetype
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

private const val BRUKERVARSEL_CREATE = "BrukervarselCreate"
private const val LEDERVARSEL_CREATE = "LedervarselCreate"
private const val ARBEIDSGIVERVARSEL_CREATE = "ArbeidsgivervarselCreate"
private const val BUDSTIKKA_SEND_TIMEOUT_MILLIS = 250L
private const val OPPFOLGING_TAG = "Oppfølging"
const val OPPFOLGINGSPLAN_CREATED_BUDSTIKKA_TEXT = "Din arbeidsgiver har laget en oppfølgingsplan for deg"
const val EVALUERINGS_PAAMINNELSE_TEXT = "Oppdater oppfølgingsplan"
const val OPPRETT_OPPFOLGINGSPLAN_PAAMINNELSE_BUDSTIKKA_TEXT = "Start oppfølgingsplan"

class BudstikkaProducer(
    private val producer: KafkaProducer<String, String>,
    private val budstikkaOppfolgingsplanSykmeldtUrl: String,
    private val dineSykmeldteOversiktUrl: String,
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
        publish(dispatch, BRUKERVARSEL_CREATE, eventId)
    }

    override suspend fun publishDineSykmeldteEvalueringspaaminnelse(
        oppfolgingsplanUuid: UUID,
        sykmeldtFnr: String,
        organisasjonsnummer: String,
        eventId: UUID,
    ): Unit = withContext(Dispatchers.IO) {
        val dispatch = Budstikka.dineSykmeldteVarselCreate(
            eventId = EventId(eventId),
            reference = oppfolgingsplanUuid.toString(),
            sykmeldt = PersonIdentifier(sykmeldtFnr),
            orgnummer = Orgnummer(organisasjonsnummer),
            oppgavetype = Oppgavetype.OPPFOLGINGSPLAN_PAAMINNELSE,
            text = EVALUERINGS_PAAMINNELSE_TEXT,
            sendingWindow = SendingWindow.ONGOING,
        )
        publish(dispatch, LEDERVARSEL_CREATE, eventId)
    }

    override suspend fun publishMinSideArbeidsgiverEvalueringspaaminnelse(
        oppfolgingsplanUuid: UUID,
        sykmeldtFnr: String,
        organisasjonsnummer: String,
        eventId: UUID,
    ): Unit = withContext(Dispatchers.IO) {
        val dispatch = Budstikka.arbeidsgivervarselCreate(
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
            tag = OPPFOLGING_TAG,
            text = EVALUERINGS_PAAMINNELSE_TEXT,
            link = dineSykmeldteOversiktUrl,
            messageType = Arbeidsgivervarsel.MessageType.BESKJED,
            sendingWindow = SendingWindow.BUDSTIKKA_OPENING_HOURS,
        )
        publish(dispatch, ARBEIDSGIVERVARSEL_CREATE, eventId)
    }

    override suspend fun publishOpprettOppfolgingsplanPaaminnelse(
        bestillingId: UUID,
        sykmeldtFnr: String,
        orgnummer: String,
        eventId: UUID,
        narmestelederId: UUID,
    ): Unit = withContext(Dispatchers.IO) {
        val dispatch = Budstikka.arbeidsgivervarselCreate(
            eventId = EventId(eventId),
            reference = bestillingId.toString(),
            orgnummer = Orgnummer(orgnummer),
            recipient = Arbeidsgivervarsel.NarmesteLeder(PersonIdentifier(sykmeldtFnr)),
            htmlEmail = Arbeidsgivervarsel.HtmlEmailNotification(
                emailTitle = OPPRETT_OPPFOLGINGSPLAN_PAAMINNELSE_EMAIL_TITLE,
                emailHtmlBody = OPPRETT_OPPFOLGINGSPLAN_PAAMINNELSE_EMAIL_HTML,
            ),
            tag = OPPFOLGING_TAG,
            text = OPPRETT_OPPFOLGINGSPLAN_PAAMINNELSE_BUDSTIKKA_TEXT,
            link = "$dineSykmeldteOversiktUrl/$narmestelederId",
            messageType = Arbeidsgivervarsel.MessageType.BESKJED,
            sendingWindow = SendingWindow.BUDSTIKKA_OPENING_HOURS,
        )

        publish(dispatch, bestillingId, eventId)
    }

    override suspend fun publishOpprettOppfolgingsplanPaaminnelseToDineSykmeldte(
        bestillingId: UUID,
        sykmeldtFnr: String,
        orgnummer: String,
        eventId: UUID,
    ): Unit = withContext(Dispatchers.IO) {
        val dispatch = Budstikka.dineSykmeldteVarselCreate(
            eventId = EventId(eventId),
            reference = bestillingId.toString(),
            sykmeldt = PersonIdentifier(sykmeldtFnr),
            orgnummer = Orgnummer(orgnummer),
            oppgavetype = Oppgavetype.OPPFOLGINGSPLAN_PAAMINNELSE,
            text = OPPRETT_OPPFOLGINGSPLAN_PAAMINNELSE_BUDSTIKKA_TEXT,
            sendingWindow = SendingWindow.ONGOING,
        )

        publish(dispatch, bestillingId, eventId)
    }

    private fun publish(
        dispatch: EncodedDispatch,
        dispatchType: String,
        eventId: UUID,
    ) = publish(dispatch, kv("type", dispatchType), eventId)

    private fun publish(
        dispatch: EncodedDispatch,
        referenceUuid: UUID,
        eventId: UUID,
    ) = publish(dispatch, kv("reference_uuid", referenceUuid), eventId)

    private fun publish(
        dispatch: EncodedDispatch,
        dispatchContext: StructuredArgument,
        eventId: UUID,
    ) {
        val record = dispatch.toProducerRecord()
        log.info(
            "Publiserer Budstikka dispatch {}, {}, {}",
            kv("topic", dispatch.topic),
            dispatchContext,
            kv("event_id", eventId),
        )
        try {
            producer.send(record).get(BUDSTIKKA_SEND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            log.error(
                "Publisert til akkumulator, timeout på get. Ukjent utfall {}, {}, {}",
                kv("topic", dispatch.topic),
                dispatchContext,
                kv("event_id", eventId),
                e,
            )
            throw e
        } catch (e: KafkaTimeoutException) {
            log.error(
                "Publisering av Budstikka dispatch timet ut. Ikke levert {}, {}, {}",
                kv("topic", dispatch.topic),
                dispatchContext,
                kv("event_id", eventId),
                e,
            )
            throw e
        } catch (e: Exception) {
            log.error(
                "Feilet ved publisering av Budstikka dispatch til {}, {}, {}",
                kv("topic", dispatch.topic),
                dispatchContext,
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
