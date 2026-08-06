package no.nav.syfo.oppfolgingsplan.db

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.syfo.TestDB
import no.nav.syfo.defaultSykmeldt
import no.nav.syfo.sykmelding.db.SykmeldingsperiodeRepository
import no.nav.syfo.sykmelding.db.domain.SykmeldingsperiodeToStore
import java.time.LocalDate

class UnntaksvurderingDAOTest :
    DescribeSpec({
        val testDb = TestDB.database
        val sykmeldingsperiodeRepository = SykmeldingsperiodeRepository(testDb)
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

        describe("softDeleteExpiredUnntaksvurderinger") {
            fun storeSykmeldingsperiode(tomMonthsAgo: Long) {
                sykmeldingsperiodeRepository.storeSykmeldingsperioder(
                    listOf(
                        SykmeldingsperiodeToStore(
                            sykmeldtFnr = sykmeldt.fnr,
                            organisasjonsnummer = sykmeldt.orgnummer,
                            sykmeldingId = "sykmelding-1",
                            fom = LocalDate.now().minusMonths(tomMonthsAgo + 1),
                            tom = LocalDate.now().minusMonths(tomMonthsAgo),
                        ),
                    ),
                )
            }

            it("soft-deletes unntaksvurdering when last tom is 7 months ago") {
                testDb.persistUnntaksvurdering(narmesteLederFnr, sykmeldt, null)
                storeSykmeldingsperiode(tomMonthsAgo = 7)

                testDb.softDeleteExpiredUnntaksvurderinger() shouldBe 1

                testDb.findAllUnntaksvurderingerBy(sykmeldt.fnr, sykmeldt.orgnummer).shouldBeEmpty()
            }

            it("does not soft-delete unntaksvurdering when last tom is 5 months ago") {
                testDb.persistUnntaksvurdering(narmesteLederFnr, sykmeldt, null)
                storeSykmeldingsperiode(tomMonthsAgo = 5)

                testDb.softDeleteExpiredUnntaksvurderinger() shouldBe 0

                testDb.findAllUnntaksvurderingerBy(sykmeldt.fnr, sykmeldt.orgnummer).size shouldBe 1
            }

            it("does not soft-delete unntaksvurdering without sykmeldingsperiode") {
                testDb.persistUnntaksvurdering(narmesteLederFnr, sykmeldt, null)

                testDb.softDeleteExpiredUnntaksvurderinger() shouldBe 0
            }

            it("does not soft-delete twice") {
                testDb.persistUnntaksvurdering(narmesteLederFnr, sykmeldt, null)
                storeSykmeldingsperiode(tomMonthsAgo = 7)

                testDb.softDeleteExpiredUnntaksvurderinger() shouldBe 1
                testDb.softDeleteExpiredUnntaksvurderinger() shouldBe 0
            }
        }
    })
