package no.nav.syfo.application.outbox

import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import no.nav.syfo.application.task.RecurringTask
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class OutboxTask(
    leaderElection: LeaderElection,
    private val outboxProcessor: OutboxProcessor,
    private val messageType: OutboxMessageType = OutboxMessageType.PAAMINNELSE_OPPFOLGINGSPLAN,
    interval: Duration = 1.minutes,
) : RecurringTask(
    name = requireNotNull(OutboxTask::class.qualifiedName),
    interval = interval,
    leaderElection = leaderElection,
) {
    override suspend fun execute() {
        val resultat = outboxProcessor.processReadyMessages(messageType)

        if (resultat.prosessert > 0) {
            log.info(
                "Prosesserte {} outbox-rader: sendt={} ikkeRelevant={} utsatt={}",
                resultat.prosessert,
                resultat.sendt,
                resultat.ikkeRelevant,
                resultat.utsatt,
            )
        } else {
            log.debug("Ingen klare outbox-rader å prosessere")
        }
    }
}
