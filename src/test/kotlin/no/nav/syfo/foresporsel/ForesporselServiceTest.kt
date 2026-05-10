package no.nav.syfo.foresporsel

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import no.nav.syfo.TestDB
import no.nav.syfo.defaultPersistedOppfolgingsplan
import no.nav.syfo.foresporsel.db.ForesporselOppfolgingsplanRepository
import no.nav.syfo.foresporsel.domain.ForesporselStatus
import no.nav.syfo.isnarmesteleder.client.IIsnarmestelederClient
import no.nav.syfo.isnarmesteleder.client.NarmesteLederRelasjonDTO
import no.nav.syfo.sykmelding.db.SykmeldingsperiodeRepository
import no.nav.syfo.sykmelding.db.domain.SykmeldingsperiodeToStore
import no.nav.syfo.varsel.EsyfovarselProducer
import no.nav.syfo.varsel.domain.ArbeidstakerHendelse
import no.nav.syfo.varsel.domain.EsyfovarselHendelse
import no.nav.syfo.varsel.domain.HendelseType
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

class ForesporselServiceTest :
    DescribeSpec({
        val sykmeldingsperiodeRepository = SykmeldingsperiodeRepository(TestDB.database)
        val foresporselRepository = ForesporselOppfolgingsplanRepository(TestDB.database)
        val isnarmestelederClient = mockk<IIsnarmestelederClient>()
        val esyfovarselProducer = mockk<EsyfovarselProducer>(relaxed = true)
        val service = ForesporselService(
            sykmeldingsperiodeRepository = sykmeldingsperiodeRepository,
            isnarmestelederClient = isnarmestelederClient,
            foresporselOppfolgingsplanRepository = foresporselRepository,
            esyfovarselProducer = esyfovarselProducer,
        )

        beforeEach {
            TestDB.clearAllData()
            clearAllMocks()
        }

        describe("getSykmeldteArbeidsforhold") {
            it("beregner status per arbeidsforhold med riktig prioritet") {
                val sykmeldtFnr = "12345678901"
                val requestedAt = Instant.now().minusSeconds(60)
                storeAktiveSykmeldinger(
                    sykmeldingsperiodeRepository = sykmeldingsperiodeRepository,
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnumre = listOf(
                        "111111111",
                        "222222222",
                        "333333333",
                        "444444444",
                        "555555555",
                    ),
                )
                coEvery { isnarmestelederClient.getNarmesteLederRelasjoner("user-token") } returns listOf(
                    activeRelasjon(
                        virksomhetsnummer = "111111111",
                        virksomhetsnavn = "Plan AS",
                        narmesteLederFnr = "10101010101",
                        narmesteLederNavn = "Plan Leder",
                    ),
                    activeRelasjon(
                        virksomhetsnummer = "333333333",
                        virksomhetsnavn = "Varsel AS",
                        narmesteLederFnr = "30303030303",
                        narmesteLederNavn = "Varsel Leder",
                    ),
                    activeRelasjon(
                        virksomhetsnummer = "444444444",
                        virksomhetsnavn = "Klar AS",
                        narmesteLederFnr = "40404040404",
                        narmesteLederNavn = "Klar Leder",
                    ),
                )
                val eksisterendePlaner = listOf(
                    defaultPersistedOppfolgingsplan().copy(
                        sykmeldtFnr = sykmeldtFnr,
                        organisasjonsnummer = "111111111",
                    ),
                    defaultPersistedOppfolgingsplan().copy(
                        sykmeldtFnr = sykmeldtFnr,
                        organisasjonsnummer = "555555555",
                    ),
                )
                foresporselRepository.storeIfNotRecentlyRequested(
                    sykmeldtFnr = sykmeldtFnr,
                    narmesteLederFnr = "30303030303",
                    organisasjonsnummer = "333333333",
                )
                setCreatedAtForForesporsler(
                    sykmeldtFnr = sykmeldtFnr,
                    createdAt = requestedAt,
                )

                val result = service.getSykmeldteArbeidsforhold(
                    sykmeldtFnr = sykmeldtFnr,
                    userToken = "user-token",
                    eksisterendePlaner = eksisterendePlaner,
                )

                result.shouldHaveSize(5)
                result[0].organisasjonsnummer shouldBe "111111111"
                result[0].foresporselStatus shouldBe ForesporselStatus.HAS_ACTIVE_PLAN
                result[0].organisasjonsnavn shouldBe "Plan AS"
                result[0].narmesteLederNavn shouldBe "Plan Leder"

                result[1].organisasjonsnummer shouldBe "222222222"
                result[1].foresporselStatus shouldBe ForesporselStatus.MISSING_NARMESTELEDER
                result[1].organisasjonsnavn shouldBe null

                result[2].organisasjonsnummer shouldBe "333333333"
                result[2].foresporselStatus shouldBe ForesporselStatus.ALREADY_REQUESTED
                result[2].organisasjonsnavn shouldBe "Varsel AS"
                result[2].narmesteLederNavn shouldBe "Varsel Leder"
                result[2].foresporselTidspunkt shouldBe requestedAt

                result[3].organisasjonsnummer shouldBe "444444444"
                result[3].foresporselStatus shouldBe ForesporselStatus.CAN_REQUEST
                result[3].organisasjonsnavn shouldBe "Klar AS"
                result[3].narmesteLederNavn shouldBe "Klar Leder"

                result[4].organisasjonsnummer shouldBe "555555555"
                result[4].foresporselStatus shouldBe ForesporselStatus.HAS_ACTIVE_PLAN
                result[4].organisasjonsnavn shouldBe null
            }

            it("returnerer CAN_REQUEST når tidligere forespørsel var til gammel nærmeste leder") {
                val sykmeldtFnr = "12345678901"
                storeAktiveSykmeldinger(
                    sykmeldingsperiodeRepository = sykmeldingsperiodeRepository,
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnumre = listOf("111111111"),
                )
                coEvery { isnarmestelederClient.getNarmesteLederRelasjoner("user-token") } returns listOf(
                    activeRelasjon(
                        virksomhetsnummer = "111111111",
                        virksomhetsnavn = "Ny Leder AS",
                        narmesteLederFnr = "10101010101",
                        narmesteLederNavn = "Ny Leder",
                    ),
                )
                foresporselRepository.storeIfNotRecentlyRequested(
                    sykmeldtFnr = sykmeldtFnr,
                    narmesteLederFnr = "20202020202",
                    organisasjonsnummer = "111111111",
                )

                val result = service.getSykmeldteArbeidsforhold(
                    sykmeldtFnr = sykmeldtFnr,
                    userToken = "user-token",
                    eksisterendePlaner = emptyList(),
                )

                result shouldBe listOf(
                    no.nav.syfo.foresporsel.domain.SykmeldtArbeidsforhold(
                        organisasjonsnummer = "111111111",
                        organisasjonsnavn = "Ny Leder AS",
                        narmesteLederNavn = "Ny Leder",
                        foresporselStatus = ForesporselStatus.CAN_REQUEST,
                        foresporselTidspunkt = null,
                    ),
                )
            }

            it("returnerer ukjent status når isnarmesteleder er utilgjengelig") {
                val sykmeldtFnr = "12345678901"
                storeAktiveSykmeldinger(
                    sykmeldingsperiodeRepository = sykmeldingsperiodeRepository,
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnumre = listOf("111111111", "222222222"),
                )
                coEvery {
                    isnarmestelederClient.getNarmesteLederRelasjoner("user-token")
                } throws RuntimeException("boom")

                val result = service.getSykmeldteArbeidsforhold(
                    sykmeldtFnr = sykmeldtFnr,
                    userToken = "user-token",
                    eksisterendePlaner = emptyList(),
                )

                result shouldBe listOf(
                    arbeidsforhold("111111111", ForesporselStatus.NARMESTELEDER_UNKNOWN),
                    arbeidsforhold("222222222", ForesporselStatus.NARMESTELEDER_UNKNOWN),
                )
            }

            it("rethrowser CancellationException fra isnarmesteleder") {
                storeAktiveSykmeldinger(
                    sykmeldingsperiodeRepository = sykmeldingsperiodeRepository,
                    sykmeldtFnr = "12345678901",
                    organisasjonsnumre = listOf("111111111"),
                )
                coEvery {
                    isnarmestelederClient.getNarmesteLederRelasjoner("user-token")
                } throws CancellationException("cancelled")

                shouldThrow<CancellationException> {
                    service.getSykmeldteArbeidsforhold(
                        sykmeldtFnr = "12345678901",
                        userToken = "user-token",
                        eksisterendePlaner = emptyList(),
                    )
                }
            }
        }

        describe("beOmPlan") {
            it("lagrer forespørsel og publiserer kafka-event") {
                val sykmeldtFnr = "12345678901"
                val hendelseSlot = slot<EsyfovarselHendelse>()
                storeAktiveSykmeldinger(
                    sykmeldingsperiodeRepository = sykmeldingsperiodeRepository,
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnumre = listOf("111111111"),
                )
                coEvery { isnarmestelederClient.getNarmesteLederRelasjoner("user-token") } returns listOf(
                    activeRelasjon(
                        virksomhetsnummer = "111111111",
                        virksomhetsnavn = "Plan AS",
                        narmesteLederFnr = "10101010101",
                        narmesteLederNavn = "Plan Leder",
                    ),
                )
                every { esyfovarselProducer.sendVarselToEsyfovarsel(capture(hendelseSlot)) } returns Unit

                service.beOmPlan(
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnummer = "111111111",
                    userToken = "user-token",
                )

                val persisted = foresporselRepository.findForesporselForSykmeldt(sykmeldtFnr)
                persisted.shouldHaveSize(1)
                persisted.single().narmesteLederFnr shouldBe "10101010101"
                persisted.single().organisasjonsnummer shouldBe "111111111"

                val hendelse = hendelseSlot.captured as ArbeidstakerHendelse
                hendelse.type shouldBe HendelseType.SM_OPPFOLGINGSPLAN_FORESPORSEL
                hendelse.arbeidstakerFnr shouldBe sykmeldtFnr
                hendelse.orgnummer shouldBe "111111111"
                hendelse.ferdigstill shouldBe false
            }

            it("kaster bad request når ingen aktiv sykmelding for virksomheten") {
                val sykmeldtFnr = "12345678901"

                val exception = shouldThrow<no.nav.syfo.application.exception.ApiErrorException.BadRequest> {
                    service.beOmPlan(
                        sykmeldtFnr = sykmeldtFnr,
                        organisasjonsnummer = "111111111",
                        userToken = "user-token",
                    )
                }

                exception.errorMessage shouldBe "Ingen aktiv sykmelding for virksomheten"
                verify(exactly = 0) { esyfovarselProducer.sendVarselToEsyfovarsel(any()) }
            }

            it("kaster conflict når forespørsel nylig er sendt") {
                val sykmeldtFnr = "12345678901"
                storeAktiveSykmeldinger(
                    sykmeldingsperiodeRepository = sykmeldingsperiodeRepository,
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnumre = listOf("111111111"),
                )
                coEvery { isnarmestelederClient.getNarmesteLederRelasjoner("user-token") } returns listOf(
                    activeRelasjon(
                        virksomhetsnummer = "111111111",
                        virksomhetsnavn = "Plan AS",
                        narmesteLederFnr = "10101010101",
                        narmesteLederNavn = "Plan Leder",
                    ),
                )
                foresporselRepository.storeIfNotRecentlyRequested(
                    sykmeldtFnr = sykmeldtFnr,
                    narmesteLederFnr = "10101010101",
                    organisasjonsnummer = "111111111",
                )

                val exception = shouldThrow<no.nav.syfo.application.exception.ApiErrorException.Conflict> {
                    service.beOmPlan(
                        sykmeldtFnr = sykmeldtFnr,
                        organisasjonsnummer = "111111111",
                        userToken = "user-token",
                    )
                }

                exception.errorMessage shouldBe "Forespørsel om oppfølgingsplan er allerede sendt nylig"
                verify(exactly = 0) { esyfovarselProducer.sendVarselToEsyfovarsel(any()) }
            }

            it("kaster not found når aktiv nærmeste leder-relasjon mangler") {
                val sykmeldtFnr = "12345678901"
                storeAktiveSykmeldinger(
                    sykmeldingsperiodeRepository = sykmeldingsperiodeRepository,
                    sykmeldtFnr = sykmeldtFnr,
                    organisasjonsnumre = listOf("111111111"),
                )
                coEvery { isnarmestelederClient.getNarmesteLederRelasjoner("user-token") } returns listOf(
                    activeRelasjon(
                        virksomhetsnummer = "222222222",
                        virksomhetsnavn = "Annen AS",
                        narmesteLederFnr = "20202020202",
                        narmesteLederNavn = "Annen Leder",
                        aktivTom = LocalDate.now(),
                    ),
                )

                val exception = shouldThrow<no.nav.syfo.application.exception.ApiErrorException.NotFound> {
                    service.beOmPlan(
                        sykmeldtFnr = sykmeldtFnr,
                        organisasjonsnummer = "111111111",
                        userToken = "user-token",
                    )
                }

                exception.errorMessage shouldBe "Fant ingen aktiv nærmeste leder-relasjon for virksomheten"
                verify(exactly = 0) { esyfovarselProducer.sendVarselToEsyfovarsel(any()) }
            }
        }
    })

private fun storeAktiveSykmeldinger(
    sykmeldingsperiodeRepository: SykmeldingsperiodeRepository,
    sykmeldtFnr: String,
    organisasjonsnumre: List<String>,
) {
    val today = LocalDate.now()
    sykmeldingsperiodeRepository.storeSykmeldingsperioder(
        organisasjonsnumre.mapIndexed { index, organisasjonsnummer ->
            SykmeldingsperiodeToStore(
                sykmeldtFnr = sykmeldtFnr,
                organisasjonsnummer = organisasjonsnummer,
                sykmeldingId = "sykmelding-$index",
                fom = today.minusDays(10),
                tom = today.plusDays(5),
            )
        },
    )
}

private fun activeRelasjon(
    virksomhetsnummer: String,
    virksomhetsnavn: String,
    narmesteLederFnr: String,
    narmesteLederNavn: String,
    aktivTom: LocalDate? = null,
) = NarmesteLederRelasjonDTO(
    uuid = virksomhetsnummer,
    virksomhetsnummer = virksomhetsnummer,
    virksomhetsnavn = virksomhetsnavn,
    narmesteLederPersonIdentNumber = narmesteLederFnr,
    narmesteLederNavn = narmesteLederNavn,
    status = "INNMELDT_AKTIV",
    aktivFom = LocalDate.now().minusDays(30),
    aktivTom = aktivTom,
)

private fun arbeidsforhold(
    organisasjonsnummer: String,
    status: ForesporselStatus,
) = no.nav.syfo.foresporsel.domain.SykmeldtArbeidsforhold(
    organisasjonsnummer = organisasjonsnummer,
    organisasjonsnavn = null,
    narmesteLederNavn = null,
    foresporselStatus = status,
    foresporselTidspunkt = null,
)

private fun setCreatedAtForForesporsler(
    sykmeldtFnr: String,
    createdAt: Instant,
) {
    TestDB.database.connection.use { connection ->
        connection.prepareStatement(
            """
            UPDATE foresporsel_oppfolgingsplan
            SET created_at = ?
            WHERE sykmeldt_fnr = ?
            """.trimIndent(),
        ).use { preparedStatement ->
            preparedStatement.setTimestamp(1, Timestamp.from(createdAt))
            preparedStatement.setString(2, sykmeldtFnr)
            preparedStatement.executeUpdate()
        }
        connection.commit()
    }
}
