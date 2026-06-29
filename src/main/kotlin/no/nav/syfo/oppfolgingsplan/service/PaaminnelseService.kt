package no.nav.syfo.oppfolgingsplan.service

import io.ktor.server.plugins.BadRequestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.oppfolgingsplan.db.domain.toStatus
import no.nav.syfo.oppfolgingsplan.db.findAllOppfolgingsplanerBy
import no.nav.syfo.oppfolgingsplan.db.findPaaminnelseBy
import no.nav.syfo.oppfolgingsplan.db.upsertPaaminnelse
import no.nav.syfo.oppfolgingsplan.dto.PaaminnelseStatus
import no.nav.syfo.oppfolgingsplan.dto.PaaminnelseStatusDto
import no.nav.syfo.sykmelding.db.SykmeldingsperiodeRepository
import java.time.Clock
import java.time.LocalDate

class PaaminnelseService(
    private val database: DatabaseInterface,
    private val sykmeldingsperiodeRepository: SykmeldingsperiodeRepository,
    private val clock: Clock = Clock.system(java.time.ZoneId.of("Europe/Oslo")),
) {
    suspend fun getPaaminnelseStatus(
        sykmeldt: Sykmeldt,
    ): PaaminnelseStatusDto = withContext(Dispatchers.IO) {
        getPaaminnelseStatusInternal(sykmeldt, LocalDate.now(clock))
    }

    suspend fun activatePaaminnelse(
        sykmeldt: Sykmeldt,
    ): PaaminnelseStatusDto = withContext(Dispatchers.IO) {
        val status = getPaaminnelseStatusInternal(sykmeldt, LocalDate.now(clock))
        requireVisiblePaaminnelseStatus(status)
        database.upsertPaaminnelse(
            sykmeldt = sykmeldt,
            bestilt = true,
        )
        PaaminnelseStatusDto(PaaminnelseStatus.BESTILT, status.synligFra)
    }

    suspend fun deactivatePaaminnelse(
        sykmeldt: Sykmeldt,
    ): PaaminnelseStatusDto = withContext(Dispatchers.IO) {
        val status = getPaaminnelseStatusInternal(sykmeldt, LocalDate.now(clock))
        requireVisiblePaaminnelseStatus(status)
        database.upsertPaaminnelse(
            sykmeldt = sykmeldt,
            bestilt = false,
        )
        PaaminnelseStatusDto(PaaminnelseStatus.TILGJENGELIG, status.synligFra)
    }

    internal fun erInnenforBestillingsvindu(
        synligFra: LocalDate,
    ): Boolean = LocalDate.now(clock).isBefore(synligFra.plusDays(PAAMINNELLSE_ETTER_DAGER))

    private fun getPaaminnelseStatusInternal(
        sykmeldt: Sykmeldt,
        today: LocalDate,
    ): PaaminnelseStatusDto {
        val synligFra = sykmeldingsperiodeRepository.findEarliestFom(
            sykmeldtFnr = sykmeldt.fnr,
            organisasjonsnummer = sykmeldt.orgnummer,
            today = today,
        )

        return when {
            sykmeldt.aktivSykmelding != true -> PaaminnelseStatusDto(PaaminnelseStatus.SKJULT, synligFra)
            synligFra == null -> PaaminnelseStatusDto(PaaminnelseStatus.SKJULT)
            database.findAllOppfolgingsplanerBy(sykmeldt.fnr, sykmeldt.orgnummer)
                .any { it.createdAt >= synligFra.atStartOfDay(clock.zone).toInstant() } ->
                PaaminnelseStatusDto(PaaminnelseStatus.SKJULT, synligFra)

            !erInnenforBestillingsvindu(synligFra) -> PaaminnelseStatusDto(PaaminnelseStatus.SKJULT, synligFra)
            else -> PaaminnelseStatusDto(
                status = database.findPaaminnelseBy(sykmeldt.fnr, sykmeldt.orgnummer).toStatus(),
                synligFra = synligFra,
            )
        }
    }

    private fun requireVisiblePaaminnelseStatus(status: PaaminnelseStatusDto) {
        if (status.status == PaaminnelseStatus.SKJULT) {
            throw BadRequestException("Kan ikke endre påminnelse når påminnelse er skjult")
        }
    }

    private companion object {
        const val PAAMINNELLSE_ETTER_DAGER = 24L
    }
}
