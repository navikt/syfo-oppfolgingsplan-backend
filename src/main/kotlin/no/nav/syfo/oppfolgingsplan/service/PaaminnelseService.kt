package no.nav.syfo.oppfolgingsplan.service

import io.ktor.server.plugins.BadRequestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.oppfolgingsplan.db.domain.toStatus
import no.nav.syfo.oppfolgingsplan.db.findAllOppfolgingsplanerBy
import no.nav.syfo.oppfolgingsplan.db.findPaaminnelseForStatus
import no.nav.syfo.oppfolgingsplan.db.upsertPaaminnelse
import no.nav.syfo.oppfolgingsplan.db.upsertPaaminnelseAndActivateOutbox
import no.nav.syfo.oppfolgingsplan.model.PaaminnelseStatus
import no.nav.syfo.oppfolgingsplan.model.Paaminnelse
import no.nav.syfo.sykmelding.db.SykmeldingsperiodeRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

const val PAAMINNELSE_ETTER_DAGER = 24L

class PaaminnelseService(
    private val database: DatabaseInterface,
    private val sykmeldingsperiodeRepository: SykmeldingsperiodeRepository,
    private val clock: Clock = Clock.system(ZoneId.of("Europe/Oslo")),
) {
    suspend fun getPaaminnelseStatus(
        sykmeldt: Sykmeldt,
    ): Paaminnelse = withContext(Dispatchers.IO) {
        getPaaminnelseStatusInternal(sykmeldt, LocalDate.now(clock))
    }

    suspend fun activatePaaminnelse(
        sykmeldt: Sykmeldt,
    ): Paaminnelse = withContext(Dispatchers.IO) {
        val status = getPaaminnelseStatusInternal(sykmeldt, LocalDate.now(clock))
        requireVisiblePaaminnelseStatus(status)
        val forlopFom = requireNotNull(status.forlopFom) {
            "forlopFom must be set when paaminnelse is visible"
        }

        database.upsertPaaminnelseAndActivateOutbox(
            sykmeldt = sykmeldt,
            forlopFom = forlopFom,
            scheduledAt = forlopFom.plusDays(PAAMINNELSE_ETTER_DAGER).atStartOfDay(clock.zone).toInstant(),
        )
        Paaminnelse(PaaminnelseStatus.BESTILT, status.forlopFom)
    }

    suspend fun deactivatePaaminnelse(
        sykmeldt: Sykmeldt,
    ): Paaminnelse = withContext(Dispatchers.IO) {
        val status = getPaaminnelseStatusInternal(sykmeldt, LocalDate.now(clock))
        requireVisiblePaaminnelseStatus(status)
        val forlopFom = requireNotNull(status.forlopFom) {
            "forlopFom must be set when paaminnelse is visible"
        }

        database.upsertPaaminnelse(
            sykmeldt = sykmeldt,
            bestilt = false,
            forlopFom = forlopFom,
        )
        Paaminnelse(PaaminnelseStatus.TILGJENGELIG, status.forlopFom)
    }

    internal fun erInnenforBestillingsvindu(
        forlopFom: LocalDate,
    ): Boolean {
        val sisteBestillingsdag = forlopFom.plusDays(PAAMINNELSE_ETTER_DAGER)
        val now = LocalDate.now(clock)
        return now in forlopFom..sisteBestillingsdag
    }

    private fun getPaaminnelseStatusInternal(
        sykmeldt: Sykmeldt,
        today: LocalDate,
    ): Paaminnelse {
        val forlopFom = sykmeldingsperiodeRepository.findEarliestFom(
            sykmeldtFnr = sykmeldt.fnr,
            organisasjonsnummer = sykmeldt.orgnummer,
            today = today,
        )

        val ikkeAktivSykmelding = sykmeldt.aktivSykmelding != true
        val harAktivOppfolgingsplan = database.findAllOppfolgingsplanerBy(sykmeldt.fnr, sykmeldt.orgnummer)
            .any { it.createdAt >= forlopFom?.atStartOfDay(clock.zone)?.toInstant() }

        return when {
            ikkeAktivSykmelding -> Paaminnelse(PaaminnelseStatus.SKJULT, forlopFom)
            forlopFom == null -> Paaminnelse(PaaminnelseStatus.SKJULT)
            harAktivOppfolgingsplan -> Paaminnelse(PaaminnelseStatus.SKJULT, forlopFom)
            !erInnenforBestillingsvindu(forlopFom) -> Paaminnelse(PaaminnelseStatus.SKJULT, forlopFom)
            else -> Paaminnelse(
                status = database.findPaaminnelseForStatus(
                    sykmeldtFnr = sykmeldt.fnr,
                    organisasjonsnummer = sykmeldt.orgnummer,
                    forlopFom = forlopFom,
                ).toStatus(),
                forlopFom = forlopFom,
            )
        }
    }

    private fun requireVisiblePaaminnelseStatus(paaminnelse: Paaminnelse) {
        if (paaminnelse.status == PaaminnelseStatus.SKJULT) {
            throw BadRequestException("Kan ikke endre påminnelse når påminnelse er skjult")
        }
    }
}
