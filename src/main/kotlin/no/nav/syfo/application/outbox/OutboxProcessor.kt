package no.nav.syfo.application.outbox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.outbox.db.claimNextReadyOutbox
import no.nav.syfo.application.outbox.db.markOutboxIkkeRelevant
import no.nav.syfo.application.outbox.db.markOutboxSent
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import no.nav.syfo.application.outbox.domain.OutboxRelevans
import no.nav.syfo.util.logger
import java.time.Clock

data class OutboxBatchResult(
    val sendt: Int = 0,
    val ikkeRelevant: Int = 0,
    val utsatt: Int = 0,
) {
    val prosessert: Int
        get() = sendt + ikkeRelevant + utsatt
}

class OutboxProcessor(
    private val database: DatabaseInterface,
    handlers: List<OutboxMessageHandler>,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = logger()
    private val handlers = handlers.associateBy { it.messageType }

    suspend fun processReadyMessages(
        messageType: OutboxMessageType,
        batchSize: Int = DEFAULT_BATCH_SIZE,
    ): OutboxBatchResult = withContext(Dispatchers.IO) {
        var resultat = OutboxBatchResult()

        repeat(batchSize) {
            ensureActive()
            when (processNextMessage(messageType) ?: return@withContext resultat) {
                Utfall.Sendt -> resultat = resultat.copy(sendt = resultat.sendt + 1)
                Utfall.IkkeRelevant -> resultat = resultat.copy(ikkeRelevant = resultat.ikkeRelevant + 1)
                Utfall.Utsatt -> return@withContext resultat.copy(utsatt = resultat.utsatt + 1)
            }
        }

        resultat
    }

    private fun processNextMessage(messageType: OutboxMessageType): Utfall? = database.connection.use { connection ->
        val now = clock.instant()
        val message = connection.claimNextReadyOutbox(messageType, now) ?: return@use null
        log.info(
            "Plukket opp outbox-melding uuid={} type={} payload={}",
            message.uuid,
            message.messageTypeValue,
            message.payload,
        )

        val handler = requireNotNull(message.messageType?.let(handlers::get)) {
            "Mangler outbox-handler for ${message.messageTypeValue}"
        }
        when (handler.evaluateRelevance(connection, message, now)) {
            OutboxRelevans.Relevant -> {
                handler.send(connection, message)
                connection.markOutboxSent(message.uuid, now)
                connection.commit()
                Utfall.Sendt
            }

            OutboxRelevans.IkkeRelevant -> {
                connection.markOutboxIkkeRelevant(message.uuid)
                connection.commit()
                Utfall.IkkeRelevant
            }

            OutboxRelevans.Utsatt -> Utfall.Utsatt
        }
    }

    private enum class Utfall {
        Sendt,
        IkkeRelevant,
        Utsatt,
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 10
    }
}
