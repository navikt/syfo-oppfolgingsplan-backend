package no.nav.syfo.oppfolgingsplan.service

import io.ktor.server.plugins.BadRequestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.outbox.domain.OutboxCancellationReason
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.oppfolgingsplan.db.PaaminnelseOutboxPayload
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedPaaminnelse
import no.nav.syfo.oppfolgingsplan.db.domain.isPaaminnelseBestiltInCurrentSykemeldingsperiode
import no.nav.syfo.oppfolgingsplan.db.existsOppfolgingsplanCreatedAfter
import no.nav.syfo.oppfolgingsplan.db.findPaaminnelseBy
import no.nav.syfo.oppfolgingsplan.db.upsertPaaminnelse
import no.nav.syfo.oppfolgingsplan.db.upsertPaaminnelseAndEnqueue
import no.nav.syfo.oppfolgingsplan.dto.PaaminnelseStatus
import no.nav.syfo.oppfolgingsplan.dto.PaaminnelseStatusDto
import no.nav.syfo.sykmelding.db.SykmeldingsperiodeRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

const val PAAMINNELSE_ETTER_DAGER = 24L

class PaaminnelseService(
    private val database: DatabaseInterface,
    private val sykmeldingsperiodeRepository: SykmeldingsperiodeRepository,
    private val clock: Clock = Clock.system(ZoneId.of("Europe/Oslo")),
) {
    suspend fun getPaaminnelseStatus(
        sykmeldt: Sykmeldt,
    ): PaaminnelseStatusDto = withContext(Dispatchers.IO) {
        resolvePaaminnelseStatus(
            sykmeldt.fnr,
            sykmeldt.orgnummer,
            LocalDate.now(clock),
        ).toPaaminnelseStatusDto(sykmeldt)
    }

    suspend fun activatePaaminnelse(
        sykmeldt: Sykmeldt,
    ): PaaminnelseStatusDto = withContext(Dispatchers.IO) {
        updatePaaminnelse(sykmeldt, bestilt = true)
    }

    suspend fun deactivatePaaminnelse(
        sykmeldt: Sykmeldt,
    ): PaaminnelseStatusDto = withContext(Dispatchers.IO) {
        updatePaaminnelse(sykmeldt, bestilt = false)
    }

    private fun erInnenforBestillingsvindu(
        sykmeldingsperiodeFom: LocalDate,
        today: LocalDate,
    ): Boolean {
        val sisteBestillingsdag = sykmeldingsperiodeFom.plusDays(PAAMINNELSE_ETTER_DAGER)
        return today in sykmeldingsperiodeFom..sisteBestillingsdag
    }

    fun getOutboxPaaminnelseStatus(
        paaminnelse: PersistedPaaminnelse,
        payload: PaaminnelseOutboxPayload,
        today: LocalDate,
    ): PaaminnelseStatusInternal {
        if (!paaminnelse.bestilt) {
            return PaaminnelseStatusInternal.Utilgjengelig(OutboxCancellationReason.NO_LONGER_REQUESTED)
        }

        if (paaminnelse.sykmeldingsperiodeId != payload.sykmeldingsperiodeId) {
            return PaaminnelseStatusInternal.Utilgjengelig(OutboxCancellationReason.SUPERSEDED)
        }

        return resolvePaaminnelseStatusForDelivery(
            sykmeldtFnr = paaminnelse.sykmeldtFnr,
            organisasjonsnummer = paaminnelse.organisasjonsnummer,
            today = today,
        )
    }

    private fun resolvePaaminnelseStatus(
        sykmeldtFnr: String,
        organisasjonsnummer: String,
        today: LocalDate,
        checkOrderingWindow: Boolean = true,
    ): PaaminnelseStatusInternal {
        val earliestSykmeldingsperiode = sykmeldingsperiodeRepository.findEarliestSykmeldingsperiode(
            sykmeldtFnr = sykmeldtFnr,
            organisasjonsnummer = organisasjonsnummer,
            today = today,
        )

        if (earliestSykmeldingsperiode == null) return PaaminnelseStatusInternal.Utilgjengelig(OutboxCancellationReason.SOURCE_NO_LONGER_ELIGIBLE)

        val sykmeldingsperiodeFom = earliestSykmeldingsperiode.fom
        if (checkOrderingWindow && !erInnenforBestillingsvindu(sykmeldingsperiodeFom, today)) {
            return PaaminnelseStatusInternal.Utilgjengelig(
                OutboxCancellationReason.SOURCE_NO_LONGER_ELIGIBLE,
            )
        }

        val harAktivOppfolgingsplan = database.existsOppfolgingsplanCreatedAfter(
            sykmeldtFnr = sykmeldtFnr,
            organisasjonsnummer = organisasjonsnummer,
            createdAfter = sykmeldingsperiodeFom.atStartOfDay(clock.zone).toInstant(),
        )
        if (harAktivOppfolgingsplan) return PaaminnelseStatusInternal.Utilgjengelig(OutboxCancellationReason.SOURCE_NO_LONGER_ELIGIBLE)

        return PaaminnelseStatusInternal.Tilgjengelig(
            sykmeldingsperiodeId = earliestSykmeldingsperiode.id,
            sykmeldingsperiodeFom = sykmeldingsperiodeFom,
        )
    }

    private fun resolvePaaminnelseStatusForDelivery(
        sykmeldtFnr: String,
        organisasjonsnummer: String,
        today: LocalDate,
    ): PaaminnelseStatusInternal = resolvePaaminnelseStatus(
        sykmeldtFnr = sykmeldtFnr,
        organisasjonsnummer = organisasjonsnummer,
        today = today,
        checkOrderingWindow = false,
    )

    private fun updatePaaminnelse(
        sykmeldt: Sykmeldt,
        bestilt: Boolean,
    ): PaaminnelseStatusDto {
        val paaminnelse = resolvePaaminnelseStatus(
            sykmeldt.fnr,
            sykmeldt.orgnummer,
            LocalDate.now(clock),
        ).requirePaaminnelseTilgjengelig()

        if (bestilt) {
            database.upsertPaaminnelseAndEnqueue(
                sykmeldt = sykmeldt,
                sykmeldingsperiodeId = paaminnelse.sykmeldingsperiodeId,
                narmestelederId = sykmeldt.narmestelederId,
                availableAt = paaminnelse.sykmeldingsperiodeFom
                    .plusDays(PAAMINNELSE_ETTER_DAGER)
                    .atStartOfDay(clock.zone)
                    .toInstant(),
            )
        } else {
            database.upsertPaaminnelse(
                sykmeldt = sykmeldt,
                bestilt = false,
                sykmeldingsperiodeId = paaminnelse.sykmeldingsperiodeId,
            )
        }

        return PaaminnelseStatusDto(
            if (bestilt) PaaminnelseStatus.BESTILT else PaaminnelseStatus.TILGJENGELIG,
        )
    }

    private fun throwPaaminnelseUtilgjengelig(): Nothing = throw BadRequestException("Kan ikke endre påminnelse når påminnelse er utilgjengelig")

    sealed interface PaaminnelseStatusInternal {
        data class Utilgjengelig(val reason: OutboxCancellationReason) : PaaminnelseStatusInternal

        data class Tilgjengelig(
            val sykmeldingsperiodeId: UUID,
            val sykmeldingsperiodeFom: LocalDate,
        ) : PaaminnelseStatusInternal
    }

    private fun PaaminnelseStatusInternal.requirePaaminnelseTilgjengelig(): PaaminnelseStatusInternal.Tilgjengelig = when (this) {
        is PaaminnelseStatusInternal.Utilgjengelig -> throwPaaminnelseUtilgjengelig()
        is PaaminnelseStatusInternal.Tilgjengelig -> this
    }

    private fun PaaminnelseStatusInternal.toPaaminnelseStatusDto(
        sykmeldt: Sykmeldt,
    ): PaaminnelseStatusDto = when (this) {
        is PaaminnelseStatusInternal.Utilgjengelig -> PaaminnelseStatusDto(PaaminnelseStatus.SKJULT)

        is PaaminnelseStatusInternal.Tilgjengelig -> {
            val paaminnelse = database.findPaaminnelseBy(
                sykmeldtFnr = sykmeldt.fnr,
                organisasjonsnummer = sykmeldt.orgnummer,
            )
            PaaminnelseStatusDto(
                if (paaminnelse?.isPaaminnelseBestiltInCurrentSykemeldingsperiode(sykmeldingsperiodeId) == true) {
                    PaaminnelseStatus.BESTILT
                } else {
                    PaaminnelseStatus.TILGJENGELIG
                },
            )
        }
    }
}
