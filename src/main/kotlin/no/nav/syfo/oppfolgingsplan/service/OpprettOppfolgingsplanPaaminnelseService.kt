package no.nav.syfo.oppfolgingsplan.service

import io.ktor.server.plugins.BadRequestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.outbox.domain.OutboxCancellationReason
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.oppfolgingsplan.db.OpprettOppfolgingsplanPaaminnelseOutboxPayload
import no.nav.syfo.oppfolgingsplan.db.deactivateOpprettOppfolgingsplanPaaminnelseAndCancelOutbox
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedOpprettOppfolgingsplanPaaminnelse
import no.nav.syfo.oppfolgingsplan.db.domain.isOpprettOppfolgingsplanPaaminnelseBestiltInCurrentSykemeldingsperiode
import no.nav.syfo.oppfolgingsplan.db.existsOppfolgingsplanCreatedAfter
import no.nav.syfo.oppfolgingsplan.db.findOpprettOppfolgingsplanPaaminnelseBy
import no.nav.syfo.oppfolgingsplan.db.upsertOpprettOppfolgingsplanPaaminnelseAndEnqueue
import no.nav.syfo.oppfolgingsplan.dto.OpprettOppfolgingsplanPaaminnelseStatus
import no.nav.syfo.oppfolgingsplan.dto.OpprettOppfolgingsplanPaaminnelseStatusDto
import no.nav.syfo.sykmelding.db.SykmeldingsperiodeRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

const val OPPRETT_OPPFOLGINGSPLAN_PAAMINNELSE_ETTER_DAGER = 24L

class OpprettOppfolgingsplanPaaminnelseService(
    private val database: DatabaseInterface,
    private val sykmeldingsperiodeRepository: SykmeldingsperiodeRepository,
    private val clock: Clock = Clock.system(ZoneId.of("Europe/Oslo")),
) {
    suspend fun getOpprettOppfolgingsplanPaaminnelseStatus(
        sykmeldt: Sykmeldt,
    ): OpprettOppfolgingsplanPaaminnelseStatusDto = withContext(Dispatchers.IO) {
        if (sykmeldt.aktivSykmelding != true) {
            return@withContext OpprettOppfolgingsplanPaaminnelseStatusDto(
                OpprettOppfolgingsplanPaaminnelseStatus.SKJULT,
            )
        }
        resolveOpprettOppfolgingsplanPaaminnelseStatus(
            sykmeldt.fnr,
            sykmeldt.orgnummer,
            LocalDate.now(clock),
        ).toOpprettOppfolgingsplanPaaminnelseStatusDto(sykmeldt)
    }

    suspend fun activateOpprettOppfolgingsplanPaaminnelse(
        sykmeldt: Sykmeldt,
    ): OpprettOppfolgingsplanPaaminnelseStatusDto = withContext(Dispatchers.IO) {
        updateOpprettOppfolgingsplanPaaminnelse(sykmeldt, bestilt = true)
    }

    suspend fun deactivateOpprettOppfolgingsplanPaaminnelse(
        sykmeldt: Sykmeldt,
    ): OpprettOppfolgingsplanPaaminnelseStatusDto = withContext(Dispatchers.IO) {
        updateOpprettOppfolgingsplanPaaminnelse(sykmeldt, bestilt = false)
    }

    private fun erInnenforBestillingsvindu(
        sykmeldingsperiodeFom: LocalDate,
        today: LocalDate,
    ): Boolean {
        val sisteBestillingsdag = sykmeldingsperiodeFom.plusDays(OPPRETT_OPPFOLGINGSPLAN_PAAMINNELSE_ETTER_DAGER)
        return today in sykmeldingsperiodeFom..sisteBestillingsdag
    }

    fun getOutboxOpprettOppfolgingsplanPaaminnelseStatus(
        opprettOppfolgingsplanPaaminnelse: PersistedOpprettOppfolgingsplanPaaminnelse,
        payload: OpprettOppfolgingsplanPaaminnelseOutboxPayload,
        today: LocalDate,
    ): OpprettOppfolgingsplanPaaminnelseStatusInternal {
        if (!opprettOppfolgingsplanPaaminnelse.bestilt) {
            return OpprettOppfolgingsplanPaaminnelseStatusInternal.Utilgjengelig(OutboxCancellationReason.NO_LONGER_REQUESTED)
        }
        if (
            payload.bestillingId != null &&
            payload.bestillingId != opprettOppfolgingsplanPaaminnelse.bestillingId
        ) {
            return OpprettOppfolgingsplanPaaminnelseStatusInternal.Utilgjengelig(OutboxCancellationReason.SUPERSEDED)
        }

        val currentOpprettOppfolgingsplanPaaminnelseStatus = resolveOpprettOppfolgingsplanPaaminnelseStatusForDelivery(
            sykmeldtFnr = opprettOppfolgingsplanPaaminnelse.sykmeldtFnr,
            organisasjonsnummer = opprettOppfolgingsplanPaaminnelse.organisasjonsnummer,
            today = today,
        )

        if (
            currentOpprettOppfolgingsplanPaaminnelseStatus is OpprettOppfolgingsplanPaaminnelseStatusInternal.Tilgjengelig &&
            !allValuesEqual(
                opprettOppfolgingsplanPaaminnelse.sykmeldingsperiodeId,
                payload.sykmeldingsperiodeId,
                currentOpprettOppfolgingsplanPaaminnelseStatus.sykmeldingsperiodeId,
            )
        ) {
            return OpprettOppfolgingsplanPaaminnelseStatusInternal.Utilgjengelig(OutboxCancellationReason.SUPERSEDED)
        }

        return currentOpprettOppfolgingsplanPaaminnelseStatus
    }

    private fun <T> allValuesEqual(vararg values: T): Boolean = values.all { value -> value == values[0] }

    private fun resolveOpprettOppfolgingsplanPaaminnelseStatus(
        sykmeldtFnr: String,
        organisasjonsnummer: String,
        today: LocalDate,
        checkOrderingWindow: Boolean = true,
    ): OpprettOppfolgingsplanPaaminnelseStatusInternal {
        val earliestSykmeldingsperiode = sykmeldingsperiodeRepository.findEarliestSykmeldingsperiode(
            sykmeldtFnr = sykmeldtFnr,
            organisasjonsnummer = organisasjonsnummer,
            today = today,
        )

        if (earliestSykmeldingsperiode == null) return OpprettOppfolgingsplanPaaminnelseStatusInternal.Utilgjengelig(OutboxCancellationReason.SOURCE_NO_LONGER_ELIGIBLE)

        val sykmeldingsperiodeFom = earliestSykmeldingsperiode.fom
        if (checkOrderingWindow && !erInnenforBestillingsvindu(sykmeldingsperiodeFom, today)) {
            return OpprettOppfolgingsplanPaaminnelseStatusInternal.Utilgjengelig(
                OutboxCancellationReason.SOURCE_NO_LONGER_ELIGIBLE,
            )
        }

        val harAktivOppfolgingsplan = database.existsOppfolgingsplanCreatedAfter(
            sykmeldtFnr = sykmeldtFnr,
            organisasjonsnummer = organisasjonsnummer,
            createdAfter = sykmeldingsperiodeFom.atStartOfDay(clock.zone).toInstant(),
        )
        if (harAktivOppfolgingsplan) return OpprettOppfolgingsplanPaaminnelseStatusInternal.Utilgjengelig(OutboxCancellationReason.SOURCE_NO_LONGER_ELIGIBLE)

        return OpprettOppfolgingsplanPaaminnelseStatusInternal.Tilgjengelig(
            sykmeldingsperiodeId = earliestSykmeldingsperiode.id,
            sykmeldingsperiodeFom = sykmeldingsperiodeFom,
        )
    }

    private fun resolveOpprettOppfolgingsplanPaaminnelseStatusForDelivery(
        sykmeldtFnr: String,
        organisasjonsnummer: String,
        today: LocalDate,
    ): OpprettOppfolgingsplanPaaminnelseStatusInternal = resolveOpprettOppfolgingsplanPaaminnelseStatus(
        sykmeldtFnr = sykmeldtFnr,
        organisasjonsnummer = organisasjonsnummer,
        today = today,
        checkOrderingWindow = false,
    )

    private fun updateOpprettOppfolgingsplanPaaminnelse(
        sykmeldt: Sykmeldt,
        bestilt: Boolean,
    ): OpprettOppfolgingsplanPaaminnelseStatusDto {
        if (sykmeldt.aktivSykmelding != true) {
            throwOpprettOppfolgingsplanPaaminnelseUtilgjengelig()
        }
        val opprettOppfolgingsplanPaaminnelse = resolveOpprettOppfolgingsplanPaaminnelseStatus(
            sykmeldt.fnr,
            sykmeldt.orgnummer,
            LocalDate.now(clock),
        ).requireOpprettOppfolgingsplanPaaminnelseTilgjengelig()

        if (bestilt) {
            database.upsertOpprettOppfolgingsplanPaaminnelseAndEnqueue(
                sykmeldt = sykmeldt,
                sykmeldingsperiodeId = opprettOppfolgingsplanPaaminnelse.sykmeldingsperiodeId,
                availableAt = opprettOppfolgingsplanPaaminnelse.sykmeldingsperiodeFom
                    .plusDays(OPPRETT_OPPFOLGINGSPLAN_PAAMINNELSE_ETTER_DAGER)
                    .atStartOfDay(clock.zone)
                    .toInstant(),
            )
        } else {
            database.deactivateOpprettOppfolgingsplanPaaminnelseAndCancelOutbox(
                sykmeldt = sykmeldt,
                sykmeldingsperiodeId = opprettOppfolgingsplanPaaminnelse.sykmeldingsperiodeId,
                completedAt = Instant.now(clock),
            )
        }

        return OpprettOppfolgingsplanPaaminnelseStatusDto(
            if (bestilt) OpprettOppfolgingsplanPaaminnelseStatus.BESTILT else OpprettOppfolgingsplanPaaminnelseStatus.TILGJENGELIG,
        )
    }

    private fun throwOpprettOppfolgingsplanPaaminnelseUtilgjengelig(): Nothing = throw BadRequestException(
        "Kan ikke endre påminnelse når påminnelse er utilgjengelig",
    )

    sealed interface OpprettOppfolgingsplanPaaminnelseStatusInternal {
        data class Utilgjengelig(val reason: OutboxCancellationReason) : OpprettOppfolgingsplanPaaminnelseStatusInternal

        data class Tilgjengelig(
            val sykmeldingsperiodeId: UUID,
            val sykmeldingsperiodeFom: LocalDate,
        ) : OpprettOppfolgingsplanPaaminnelseStatusInternal
    }

    private fun OpprettOppfolgingsplanPaaminnelseStatusInternal.requireOpprettOppfolgingsplanPaaminnelseTilgjengelig(): OpprettOppfolgingsplanPaaminnelseStatusInternal.Tilgjengelig = when (this) {
        is OpprettOppfolgingsplanPaaminnelseStatusInternal.Utilgjengelig -> throwOpprettOppfolgingsplanPaaminnelseUtilgjengelig()
        is OpprettOppfolgingsplanPaaminnelseStatusInternal.Tilgjengelig -> this
    }

    private fun OpprettOppfolgingsplanPaaminnelseStatusInternal.toOpprettOppfolgingsplanPaaminnelseStatusDto(
        sykmeldt: Sykmeldt,
    ): OpprettOppfolgingsplanPaaminnelseStatusDto = when (this) {
        is OpprettOppfolgingsplanPaaminnelseStatusInternal.Utilgjengelig ->
            OpprettOppfolgingsplanPaaminnelseStatusDto(OpprettOppfolgingsplanPaaminnelseStatus.SKJULT)

        is OpprettOppfolgingsplanPaaminnelseStatusInternal.Tilgjengelig -> {
            val opprettOppfolgingsplanPaaminnelse = database.findOpprettOppfolgingsplanPaaminnelseBy(
                sykmeldtFnr = sykmeldt.fnr,
                organisasjonsnummer = sykmeldt.orgnummer,
            )
            OpprettOppfolgingsplanPaaminnelseStatusDto(
                if (opprettOppfolgingsplanPaaminnelse?.isOpprettOppfolgingsplanPaaminnelseBestiltInCurrentSykemeldingsperiode(
                        sykmeldingsperiodeId,
                    ) == true
                ) {
                    OpprettOppfolgingsplanPaaminnelseStatus.BESTILT
                } else {
                    OpprettOppfolgingsplanPaaminnelseStatus.TILGJENGELIG
                },
            )
        }
    }
}
