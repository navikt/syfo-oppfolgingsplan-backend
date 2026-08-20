package no.nav.syfo.oppfolgingsplan.service

import io.ktor.server.plugins.BadRequestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.oppfolgingsplan.db.domain.isPaaminnelseBestiltInCurrentSykemeldingsperiode
import no.nav.syfo.oppfolgingsplan.db.existsOppfolgingsplanCreatedAfter
import no.nav.syfo.oppfolgingsplan.db.findPaaminnelseBy
import no.nav.syfo.oppfolgingsplan.db.upsertPaaminnelse
import no.nav.syfo.oppfolgingsplan.dto.PaaminnelseStatus
import no.nav.syfo.oppfolgingsplan.dto.PaaminnelseStatusDto
import no.nav.syfo.sykmelding.db.SykmeldingsperiodeRepository
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

const val PAAMINNELSE_ETTER_DAGER = 24L

class PaaminnelseService(
    private val database: DatabaseInterface,
    private val sykmeldingsperiodeRepository: SykmeldingsperiodeRepository,
    private val clock: Clock = Clock.system(java.time.ZoneId.of("Europe/Oslo")),
) {
    suspend fun getPaaminnelseStatus(
        sykmeldt: Sykmeldt,
    ): PaaminnelseStatusDto = withContext(Dispatchers.IO) {
        resolvePaaminnelseStatus(sykmeldt, LocalDate.now(clock)).toPaaminnelseStatusDto(sykmeldt)
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

    internal fun erInnenforBestillingsvindu(
        sykmeldingsperiodeFom: LocalDate,
    ): Boolean {
        val sisteBestillingsdag = sykmeldingsperiodeFom.plusDays(PAAMINNELSE_ETTER_DAGER)
        val now = LocalDate.now(clock)
        return now in sykmeldingsperiodeFom..sisteBestillingsdag
    }

    private fun resolvePaaminnelseStatus(
        sykmeldt: Sykmeldt,
        today: LocalDate,
    ): PaaminnelseStatusInternal {
        if (sykmeldt.aktivSykmelding != true) return PaaminnelseStatusInternal.Skjult

        val earliestSykmeldingsperiode = sykmeldingsperiodeRepository.findEarliestSykmeldingsperiode(
            sykmeldtFnr = sykmeldt.fnr,
            organisasjonsnummer = sykmeldt.orgnummer,
            today = today,
        )

        if (earliestSykmeldingsperiode == null) return PaaminnelseStatusInternal.Skjult

        val sykmeldingsperiodeFom = earliestSykmeldingsperiode.fom
        if (!erInnenforBestillingsvindu(sykmeldingsperiodeFom)) return PaaminnelseStatusInternal.Skjult

        val harAktivOppfolgingsplan = database.existsOppfolgingsplanCreatedAfter(
            sykmeldtFnr = sykmeldt.fnr,
            organisasjonsnummer = sykmeldt.orgnummer,
            createdAfter = sykmeldingsperiodeFom.atStartOfDay(clock.zone).toInstant(),
        )
        if (harAktivOppfolgingsplan) return PaaminnelseStatusInternal.Skjult

        return PaaminnelseStatusInternal.PaaminnelseTilgjengelig(
            sykmeldingsperiodeId = earliestSykmeldingsperiode.id,
        )
    }

    private fun updatePaaminnelse(
        sykmeldt: Sykmeldt,
        bestilt: Boolean,
    ): PaaminnelseStatusDto {
        val paaminnelse = resolvePaaminnelseStatus(sykmeldt, LocalDate.now(clock))
            .requirePaaminnelseTilgjengelig()
        database.upsertPaaminnelse(
            sykmeldt = sykmeldt,
            bestilt = bestilt,
            sykmeldingsperiodeId = paaminnelse.sykmeldingsperiodeId,
        )

        return PaaminnelseStatusDto(
            if (bestilt) PaaminnelseStatus.BESTILT else PaaminnelseStatus.TILGJENGELIG,
        )
    }

    private fun throwPaaminnelseUtilgjengelig(): Nothing = throw BadRequestException(
        "Kan ikke endre påminnelse når påminnelse er utilgjengelig",
    )

    private sealed interface PaaminnelseStatusInternal {
        data object Skjult : PaaminnelseStatusInternal

        data class PaaminnelseTilgjengelig(
            val sykmeldingsperiodeId: UUID,
        ) : PaaminnelseStatusInternal
    }

    private fun PaaminnelseStatusInternal.requirePaaminnelseTilgjengelig(): PaaminnelseStatusInternal.PaaminnelseTilgjengelig = when (this) {
        is PaaminnelseStatusInternal.Skjult -> throwPaaminnelseUtilgjengelig()
        is PaaminnelseStatusInternal.PaaminnelseTilgjengelig -> this
    }

    private fun PaaminnelseStatusInternal.toPaaminnelseStatusDto(
        sykmeldt: Sykmeldt,
    ): PaaminnelseStatusDto = when (this) {
        is PaaminnelseStatusInternal.Skjult -> PaaminnelseStatusDto(PaaminnelseStatus.SKJULT)
        is PaaminnelseStatusInternal.PaaminnelseTilgjengelig -> {
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
