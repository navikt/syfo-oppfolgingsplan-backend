package no.nav.syfo.oppfolgingsplan.dto

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.syfo.oppfolgingsplan.domain.OrganizationDetails
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class UtledGjeldendeStatusTest :
    DescribeSpec({
        val organization = OrganizationDetails(orgNumber = "orgnummer", orgName = "Test AS")
        val utkast = UtkastMetadata(
            sistLagretTidspunkt = Instant.now(),
            utkastUtloperDato = Instant.now(),
        )
        val aktivPlan = OppfolgingsplanMetadata(
            id = UUID.randomUUID(),
            evalueringsDato = LocalDate.now().plusDays(30),
            ferdigstiltTidspunkt = Instant.now(),
            stillingstittel = "Systemutvikler",
            stillingsprosent = BigDecimal("100.00"),
            organization = organization,
        )
        val unntaksvurdering = UnntaksvurderingMetadata(
            id = UUID.randomUUID(),
            meldtTidspunkt = Instant.now(),
            meldtAv = MeldtAv(navn = "Maren Hegna", rolle = MeldtAvRolle.ARBEIDSGIVER),
            organization = organization,
        )

        describe("utledGjeldendeStatus") {
            it("returns INGEN when nothing exists") {
                utledGjeldendeStatus(null, null, emptyList()) shouldBe GjeldendeStatus.INGEN
            }

            it("returns IKKE_AKTUELT when only unntaksvurderinger exist") {
                utledGjeldendeStatus(null, null, listOf(unntaksvurdering)) shouldBe GjeldendeStatus.IKKE_AKTUELT
            }

            it("returns UTKAST when utkast exists, regardless of unntaksvurderinger") {
                utledGjeldendeStatus(utkast, null, listOf(unntaksvurdering)) shouldBe GjeldendeStatus.UTKAST
            }

            it("returns AKTIV_PLAN when aktiv plan exists, regardless of utkast and unntaksvurderinger") {
                utledGjeldendeStatus(utkast, aktivPlan, listOf(unntaksvurdering)) shouldBe GjeldendeStatus.AKTIV_PLAN
            }
        }
    })
