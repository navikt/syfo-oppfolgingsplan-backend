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
const val EVALUERINGS_PAAMINNELSE_EMAIL_TITLE = "Oppdater oppfølgingsplanen"
val EVALUERINGS_PAAMINNELSE_EMAIL_HTML = """
    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="width: 100%; max-width: 640px; margin: 0 auto; border: 1px solid #d8d8d8; border-radius: 8px; background-color: #ffffff; color: #262626; font-family: Arial, sans-serif;">
      <tbody>
        <tr>
          <td style="padding: 28px 32px; background-color: #004367; color: #ffffff;">
            <span aria-hidden="true" style="margin-right: 12px; font-size: 24px;">&#9993;&#65039;</span>
            <span style="font-size: 24px; font-weight: 700; line-height: 1.3;">Oppdater oppfølgingsplanen</span>
          </td>
        </tr>
        <tr>
          <td style="padding: 32px; font-size: 18px; line-height: 1.5;">
            <p style="margin: 0 0 24px;">Hei,</p>
            <p style="margin: 0 0 32px;">Det er tid for å vurdere om situasjonen til den som er sykmeldt er annerledes enn tidligere og at det derfor er riktig å gjøre endringer i oppfølgingsplanen. Ta en prat for å finne ut om det er aktuelt nå eller at dere lager en ny avtale litt frem i tid.</p>
            <p style="margin: 0 0 24px; font-weight: 700;">Gå til Min side – arbeidsgiver på nav.no for å oppdatere oppfølgingsplanen.</p>
            <hr style="margin: 0 0 24px; border: 0; border-top: 1px solid #d8d8d8;">
            <p style="margin: 0 0 20px;">Har du spørsmål? Ring oss på 55 55 33 36.</p>
            <p style="margin: 0 0 20px;">Du kan ikke svare på denne meldingen.</p>
            <p style="margin: 0;">Vennlig hilsen Nav</p>
          </td>
        </tr>
      </tbody>
    </table>
""".trimIndent()
const val PAAMINNELSE_BUDSTIKKA_TEXT = "Start oppfølgingsplan"

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

    override suspend fun publishPaaminnelse(
        paaminnelseUuid: UUID,
        sykmeldtFnr: String,
        orgnummer: String,
        eventId: UUID,
        narmestelederId: String,
    ): Unit = withContext(Dispatchers.IO) {
        val dispatch = Budstikka.arbeidsgivervarselCreate(
            eventId = EventId(eventId),
            reference = paaminnelseUuid.toString(),
            orgnummer = Orgnummer(orgnummer),
            recipient = Arbeidsgivervarsel.NarmesteLeder(
                sykmeldt = PersonIdentifier(sykmeldtFnr),
                externalNotification = Arbeidsgivervarsel.NarmesteLederExternalNotification(
                    emailTitle = "Title",
                    emailText = "<body></body>",
                ),
            ),
            tag = OPPFOLGING_TAG,
            text = PAAMINNELSE_BUDSTIKKA_TEXT,
            link = "$dineSykmeldteOversiktUrl/$narmestelederId",
            messageType = Arbeidsgivervarsel.MessageType.BESKJED,
            sendingWindow = SendingWindow.BUDSTIKKA_OPENING_HOURS,
        )

        publish(dispatch, paaminnelseUuid, eventId)
    }

    override suspend fun publishPaaminnelseToDineSykmeldte(
        paaminnelseUuid: UUID,
        sykmeldtFnr: String,
        orgnummer: String,
        eventId: UUID,
        narmestelederId: String,
    ): Unit = withContext(Dispatchers.IO) {
        val dispatch = Budstikka.dineSykmeldteVarselCreate(
            eventId = EventId(eventId),
            reference = paaminnelseUuid.toString(),
            sykmeldt = PersonIdentifier(sykmeldtFnr),
            orgnummer = Orgnummer(orgnummer),
            oppgavetype = Oppgavetype.OPPFOLGINGSPLAN_PAAMINNELSE,
            text = PAAMINNELSE_BUDSTIKKA_TEXT,
            link = "$dineSykmeldteOversiktUrl/$narmestelederId",
            sendingWindow = SendingWindow.BUDSTIKKA_OPENING_HOURS,
        )

        publish(dispatch, paaminnelseUuid, eventId)
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
