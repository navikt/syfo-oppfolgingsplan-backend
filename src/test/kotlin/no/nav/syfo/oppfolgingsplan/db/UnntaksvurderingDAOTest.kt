package no.nav.syfo.oppfolgingsplan.db

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.syfo.TestDB
import no.nav.syfo.defaultSykmeldt

class UnntaksvurderingDAOTest :
    DescribeSpec({
        val testDb = TestDB.database
        val sykmeldt = defaultSykmeldt()
        val narmesteLederFnr = "10987654321"

        beforeTest {
            TestDB.clearAllData()
        }

        describe("persistUnntaksvurdering and findAllUnntaksvurderingerBy") {
            it("persists and reads back an unntaksvurdering") {
                val uuid = testDb.persistUnntaksvurdering(narmesteLederFnr, sykmeldt, "Maren Hegna")

                val result = testDb.findAllUnntaksvurderingerBy(sykmeldt.fnr, sykmeldt.orgnummer)

                result.size shouldBe 1
                result.first().uuid shouldBe uuid
                result.first().sykmeldtFnr shouldBe sykmeldt.fnr
                result.first().organisasjonsnummer shouldBe sykmeldt.orgnummer
                result.first().narmesteLederFnr shouldBe narmesteLederFnr
                result.first().narmesteLederFullName shouldBe "Maren Hegna"
                result.first().createdAt.shouldNotBeNull()
                result.first().skjultFra shouldBe null
            }

            it("allows null narmesteLederFullName") {
                testDb.persistUnntaksvurdering(narmesteLederFnr, sykmeldt, null)

                testDb.findAllUnntaksvurderingerBy(sykmeldt.fnr, sykmeldt.orgnummer)
                    .first()
                    .narmesteLederFullName shouldBe null
            }

            it("returns newest first") {
                val first = testDb.persistUnntaksvurdering(narmesteLederFnr, sykmeldt, null)
                val second = testDb.persistUnntaksvurdering(narmesteLederFnr, sykmeldt, null)

                val result = testDb.findAllUnntaksvurderingerBy(sykmeldt.fnr, sykmeldt.orgnummer)

                result.map { it.uuid } shouldBe listOf(second, first)
            }

            it("does not return unntaksvurderinger for other arbeidsforhold") {
                testDb.persistUnntaksvurdering(narmesteLederFnr, sykmeldt, null)

                testDb.findAllUnntaksvurderingerBy(sykmeldt.fnr, "annetorgnummer").shouldBeEmpty()
                testDb.findAllUnntaksvurderingerBy("99999999999", sykmeldt.orgnummer).shouldBeEmpty()
            }
        }
    })
