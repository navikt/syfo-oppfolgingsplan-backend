package no.nav.syfo.application

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ApplicationEnvironmentTest :
    DescribeSpec({
        describe("LocalEnvironment") {
            it("uses dev URL for Budstikka oppfolgingsplan link") {
                LocalEnvironment().minSideSykmeldtOppfolgingsplanUrl shouldBe
                    "https://www.ekstern.dev.nav.no/syk/oppfolgingsplan/sykmeldt"
            }
        }
    })
