package no.nav.syfo.oppfolgingsplan.service

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.oppfolgingsplan.db.EvalueringspaaminnelseSourceFacts
import no.nav.syfo.oppfolgingsplan.db.EvalueringspaaminnelseSourceRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class EvalueringspaaminnelseEligibilityServiceTest :
    DescribeSpec({
        val repository = mockk<EvalueringspaaminnelseSourceRepository>()
        val service = EvalueringspaaminnelseEligibilityService(repository)
        val oppfolgingsplanUuid = UUID.randomUUID()
        val now = Instant.parse("2026-05-20T22:30:00Z")
        val todayInOslo = LocalDate.of(2026, 5, 21)
        val eligibleFacts = EvalueringspaaminnelseSourceFacts(
            sykmeldtFnr = "12345678901",
            organisasjonsnummer = "999999999",
            isHidden = false,
            isRegisteredIncorrectly = false,
            hasActiveSykmeldingsperiode = true,
        )

        it("uses the date in Europe/Oslo and returns an eligible recipient") {
            coEvery {
                repository.findSourceFacts(oppfolgingsplanUuid, todayInOslo)
            } returns eligibleFacts

            val result = service.resolve(oppfolgingsplanUuid, now)

            result shouldBe EvalueringspaaminnelseEligibility.Eligible(
                EvalueringspaaminnelseRecipient(
                    sykmeldtFnr = eligibleFacts.sykmeldtFnr,
                    organisasjonsnummer = eligibleFacts.organisasjonsnummer,
                ),
            )
            result.toString().contains(eligibleFacts.sykmeldtFnr) shouldBe false
            result.toString().contains(eligibleFacts.organisasjonsnummer) shouldBe false
            coVerify(exactly = 1) {
                repository.findSourceFacts(oppfolgingsplanUuid, todayInOslo)
            }
        }

        it("returns not found when the source plan does not exist") {
            coEvery { repository.findSourceFacts(oppfolgingsplanUuid, todayInOslo) } returns null

            service.resolve(oppfolgingsplanUuid, now) shouldBe
                EvalueringspaaminnelseEligibility.NotFound
        }

        listOf(
            "hidden plan" to eligibleFacts.copy(isHidden = true),
            "incorrectly registered plan" to eligibleFacts.copy(isRegisteredIncorrectly = true),
            "plan without an active sykmeldingsperiode" to
                eligibleFacts.copy(hasActiveSykmeldingsperiode = false),
        ).forEach { (scenario, facts) ->
            it("returns no longer eligible for $scenario") {
                coEvery {
                    repository.findSourceFacts(oppfolgingsplanUuid, todayInOslo)
                } returns facts

                service.resolve(oppfolgingsplanUuid, now) shouldBe
                    EvalueringspaaminnelseEligibility.NoLongerEligible
            }
        }
    })
