package no.nav.syfo.application

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ApplicationEnvironmentTest :
    DescribeSpec({
        describe("isBudstikkaShadowEnabled") {
            it("returns true when Budstikka is enabled outside prod") {
                isBudstikkaShadowEnabled(
                    budstikkaEnabled = true,
                    isProdEnv = false,
                ) shouldBe true
            }

            it("returns false when Budstikka is enabled in prod") {
                isBudstikkaShadowEnabled(
                    budstikkaEnabled = true,
                    isProdEnv = true,
                ) shouldBe false
            }

            it("returns false when Budstikka is disabled outside prod") {
                isBudstikkaShadowEnabled(
                    budstikkaEnabled = false,
                    isProdEnv = false,
                ) shouldBe false
            }
        }

        describe("LocalEnvironment") {
            it("uses dev URL for Budstikka oppfolgingsplan link") {
                LocalEnvironment().budstikkaOppfolgingsplanSykmeldtUrl shouldBe
                    "https://www.ekstern.dev.nav.no/syk/oppfolgingsplan/sykmeldt"
            }
        }
    })
