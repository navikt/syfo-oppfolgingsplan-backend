package no.nav.syfo.oppfolgingsplan.outbox

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val ZONE_OSLO: ZoneId = ZoneId.of("Europe/Oslo")
private const val EVALUERING_PAAMINNELSE_DAYS_BEFORE = 3L
private const val EVALUERING_PAAMINNELSE_HOUR = 9

data class EvalueringPaaminnelseDefinition(
    val messageType: OppfolgingsplanOutboxMessageType,
    val availableAt: Instant,
)

object EvalueringPaaminnelseFactory {
    fun create(
        enabled: Boolean,
        evalueringsdato: LocalDate,
    ): List<EvalueringPaaminnelseDefinition> {
        if (!enabled) return emptyList()

        val availableAt = evalueringsdato
            .minusDays(EVALUERING_PAAMINNELSE_DAYS_BEFORE)
            .atTime(EVALUERING_PAAMINNELSE_HOUR, 0)
            .atZone(ZONE_OSLO)
            .toInstant()

        return OppfolgingsplanOutboxMessageType.evalueringPaaminnelseTypes.map { messageType ->
            EvalueringPaaminnelseDefinition(
                messageType = messageType,
                availableAt = availableAt,
            )
        }
    }
}
