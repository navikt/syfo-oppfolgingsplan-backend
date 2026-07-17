package no.nav.syfo.varsel.budstikka

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.syfo.varsel.budstikka.domain.BrukervarselCreate
import no.nav.syfo.varsel.budstikka.domain.Varseltype
import java.util.UUID

class OppfolgingsplanCreatedBudstikkaMapperTest :
    DescribeSpec({
        describe("createOppfolgingsplanCreatedDispatch") {
            it("maps oppfolgingsplan created to Budstikka dispatch") {
                val oppfolgingsplanUuid = UUID.fromString("0a5c80b8-2350-4f2a-b0e7-d1b796c6c8d4")
                val budstikkaOppfolgingsplanSykmeldtUrl = "https://www.nav.no/syk/oppfolgingsplan/sykmeldt"

                val dispatch = createOppfolgingsplanCreatedDispatch(
                    oppfolgingsplanUuid = oppfolgingsplanUuid,
                    sykmeldtFnr = "12345678901",
                    budstikkaOppfolgingsplanSykmeldtUrl = budstikkaOppfolgingsplanSykmeldtUrl,
                )

                dispatch.reference shouldBe oppfolgingsplanUuid.toString()
                val content = dispatch.content as BrukervarselCreate
                content.personIdentifier.value shouldBe "12345678901"
                content.varseltype shouldBe Varseltype.BESKJED
                content.text shouldBe OPPFOLGINGSPLAN_CREATED_BUDSTIKKA_TEXT
                content.link shouldBe budstikkaOppfolgingsplanSykmeldtUrl
            }
        }
    })
