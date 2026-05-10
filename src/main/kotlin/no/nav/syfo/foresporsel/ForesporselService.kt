package no.nav.syfo.foresporsel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.application.exception.ApiErrorException
import no.nav.syfo.foresporsel.db.ForesporselOppfolgingsplanRepository
import no.nav.syfo.foresporsel.domain.ForesporselStatus
import no.nav.syfo.foresporsel.domain.SykmeldtArbeidsforhold
import no.nav.syfo.isnarmesteleder.client.IIsnarmestelederClient
import no.nav.syfo.isnarmesteleder.client.NarmesteLederRelasjonDTO
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedOppfolgingsplan
import no.nav.syfo.sykmelding.db.FORESPORSEL_GRACE_PERIOD_DAYS
import no.nav.syfo.sykmelding.db.SykmeldingsperiodeRepository
import no.nav.syfo.util.logger
import no.nav.syfo.varsel.EsyfovarselProducer
import no.nav.syfo.varsel.domain.ArbeidstakerHendelse
import no.nav.syfo.varsel.domain.HendelseType
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val NARMESTELEDER_STATUS_INNMELDT_AKTIV = "INNMELDT_AKTIV"

class ForesporselService(
    private val sykmeldingsperiodeRepository: SykmeldingsperiodeRepository,
    private val isnarmestelederClient: IIsnarmestelederClient,
    private val foresporselOppfolgingsplanRepository: ForesporselOppfolgingsplanRepository,
    private val esyfovarselProducer: EsyfovarselProducer,
) {
    private val logger = logger()

    suspend fun getSykmeldteArbeidsforhold(
        sykmeldtFnr: String,
        userToken: String,
        eksisterendePlaner: List<PersistedOppfolgingsplan>,
    ): List<SykmeldtArbeidsforhold> {
        val organisasjonsnumre = withContext(Dispatchers.IO) {
            sykmeldingsperiodeRepository.findOrganisasjonerMedAktivSykmeldingsperiode(sykmeldtFnr)
        }.sorted()

        if (organisasjonsnumre.isEmpty()) {
            return emptyList()
        }

        val organisasjonerMedAktivPlan = eksisterendePlaner
            .map { it.organisasjonsnummer }
            .toSet()

        val foresporsler = withContext(Dispatchers.IO) {
            foresporselOppfolgingsplanRepository.findForesporselForSykmeldt(sykmeldtFnr)
        }
        val gracePeriodCutoff = Instant.now().minus(FORESPORSEL_GRACE_PERIOD_DAYS, ChronoUnit.DAYS)

        val aktiveNarmesteLederRelasjoner = try {
            isnarmestelederClient.getNarmesteLederRelasjoner(userToken)
                .filter { it.isActive() }
                .associateBy { it.virksomhetsnummer }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Kunne ikke hente nærmeste leder-relasjoner, returnerer ukjent forespørselstatus", e)
            null
        }

        return organisasjonsnumre.map { organisasjonsnummer ->
            val narmesteLederRelasjon = aktiveNarmesteLederRelasjoner?.get(organisasjonsnummer)
            val harAktivPlan = organisasjonsnummer in organisasjonerMedAktivPlan

            val (status, tidspunkt) = bestemStatus(
                harAktivPlan = harAktivPlan,
                narmesteLederRelasjon = narmesteLederRelasjon,
                nlTilgjengelig = aktiveNarmesteLederRelasjoner != null,
                foresporsler = foresporsler,
                gracePeriodCutoff = gracePeriodCutoff,
            )

            SykmeldtArbeidsforhold(
                organisasjonsnummer = organisasjonsnummer,
                organisasjonsnavn = narmesteLederRelasjon?.virksomhetsnavn,
                narmesteLederNavn = narmesteLederRelasjon?.narmesteLederNavn,
                foresporselStatus = status,
                foresporselTidspunkt = tidspunkt,
            )
        }
    }

    private fun bestemStatus(
        harAktivPlan: Boolean,
        narmesteLederRelasjon: NarmesteLederRelasjonDTO?,
        nlTilgjengelig: Boolean,
        foresporsler: List<no.nav.syfo.foresporsel.domain.PersistedForesporsel>,
        gracePeriodCutoff: Instant,
    ): Pair<ForesporselStatus, Instant?> = when {
        harAktivPlan -> ForesporselStatus.HAS_ACTIVE_PLAN to null

        !nlTilgjengelig -> ForesporselStatus.NARMESTELEDER_UNKNOWN to null

        narmesteLederRelasjon == null -> ForesporselStatus.MISSING_NARMESTELEDER to null

        else -> {
            val nyligForesporsel = foresporsler.firstOrNull {
                it.narmesteLederFnr == narmesteLederRelasjon.narmesteLederPersonIdentNumber &&
                    it.createdAt.isAfter(gracePeriodCutoff)
            }
            if (nyligForesporsel != null) {
                ForesporselStatus.ALREADY_REQUESTED to nyligForesporsel.createdAt
            } else {
                ForesporselStatus.CAN_REQUEST to null
            }
        }
    }

    suspend fun beOmPlan(
        sykmeldtFnr: String,
        organisasjonsnummer: String,
        userToken: String,
    ) {
        val aktiveOrganisasjoner = withContext(Dispatchers.IO) {
            sykmeldingsperiodeRepository.findOrganisasjonerMedAktivSykmeldingsperiode(sykmeldtFnr)
        }
        if (organisasjonsnummer !in aktiveOrganisasjoner) {
            throw ApiErrorException.BadRequest("Ingen aktiv sykmelding for virksomheten")
        }

        val narmesteLederRelasjon = isnarmestelederClient.getNarmesteLederRelasjoner(userToken)
            .firstOrNull {
                it.virksomhetsnummer == organisasjonsnummer && it.isActive()
            }
            ?: throw ApiErrorException.NotFound("Fant ingen aktiv nærmeste leder-relasjon for virksomheten")

        val foresporselId = withContext(Dispatchers.IO) {
            foresporselOppfolgingsplanRepository.storeIfNotRecentlyRequested(
                sykmeldtFnr = sykmeldtFnr,
                narmesteLederFnr = narmesteLederRelasjon.narmesteLederPersonIdentNumber,
                organisasjonsnummer = organisasjonsnummer,
            )
        } ?: throw ApiErrorException.Conflict("Forespørsel om oppfølgingsplan er allerede sendt nylig")

        try {
            withContext(Dispatchers.IO) {
                esyfovarselProducer.sendVarselToEsyfovarsel(
                    ArbeidstakerHendelse(
                        type = HendelseType.SM_OPPFOLGINGSPLAN_FORESPORSEL,
                        ferdigstill = false,
                        data = null,
                        arbeidstakerFnr = sykmeldtFnr,
                        orgnummer = organisasjonsnummer,
                    ),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Kunne ikke sende Kafka-varsel for forespørsel $foresporselId (org $organisasjonsnummer)", e)
        }
    }
}

private fun NarmesteLederRelasjonDTO.isActive(): Boolean = status == NARMESTELEDER_STATUS_INNMELDT_AKTIV && aktivTom == null
