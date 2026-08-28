package no.nav.syfo.oppfolgingsplan.service

import no.nav.syfo.oppfolgingsplan.db.EvalueringspaaminnelseSourceRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

private val ZONE_OSLO: ZoneId = ZoneId.of("Europe/Oslo")

class EvalueringspaaminnelseEligibilityService(
    private val repository: EvalueringspaaminnelseSourceRepository,
) {
    suspend fun resolve(
        oppfolgingsplanUuid: UUID,
        now: Instant,
    ): EvalueringspaaminnelseEligibility {
        val sourceFacts = repository.findSourceFacts(
            oppfolgingsplanUuid = oppfolgingsplanUuid,
            today = LocalDate.ofInstant(now, ZONE_OSLO),
        ) ?: return EvalueringspaaminnelseEligibility.NotFound

        if (
            sourceFacts.isHidden ||
            sourceFacts.isRegisteredIncorrectly ||
            !sourceFacts.hasActiveSykmeldingsperiode
        ) {
            return EvalueringspaaminnelseEligibility.NoLongerEligible
        }

        return EvalueringspaaminnelseEligibility.Eligible(
            EvalueringspaaminnelseRecipient(
                sykmeldtFnr = sourceFacts.sykmeldtFnr,
                organisasjonsnummer = sourceFacts.organisasjonsnummer,
            ),
        )
    }
}

sealed interface EvalueringspaaminnelseEligibility {
    data class Eligible(
        val recipient: EvalueringspaaminnelseRecipient,
    ) : EvalueringspaaminnelseEligibility

    data object NotFound : EvalueringspaaminnelseEligibility

    data object NoLongerEligible : EvalueringspaaminnelseEligibility
}

data class EvalueringspaaminnelseRecipient(
    val sykmeldtFnr: String,
    val organisasjonsnummer: String,
) {
    override fun toString(): String = "EvalueringspaaminnelseRecipient()"
}
