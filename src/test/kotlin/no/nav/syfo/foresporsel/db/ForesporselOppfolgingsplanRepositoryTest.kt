package no.nav.syfo.foresporsel.db

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.syfo.TestDB
import no.nav.syfo.sykmelding.db.FORESPORSEL_GRACE_PERIOD_DAYS
import java.sql.Timestamp
import java.time.Instant

class ForesporselOppfolgingsplanRepositoryTest :
    DescribeSpec({
        val repository = ForesporselOppfolgingsplanRepository(TestDB.database)

        beforeEach {
            TestDB.clearAllData()
        }

        describe("storeIfNotRecentlyRequested") {
            it("stores request and returns id when no recent request exists") {
                val insertedId = repository.storeIfNotRecentlyRequested(
                    sykmeldtFnr = "12345678901",
                    narmesteLederFnr = "10987654321",
                    organisasjonsnummer = "999888777",
                )

                val persisted = repository.findForesporselForSykmeldt("12345678901")

                insertedId shouldBe persisted.single().id
            }

            it("returns null when a recent request already exists") {
                repository.storeIfNotRecentlyRequested(
                    sykmeldtFnr = "12345678901",
                    narmesteLederFnr = "10987654321",
                    organisasjonsnummer = "999888777",
                )

                val duplicateInsertId = repository.storeIfNotRecentlyRequested(
                    sykmeldtFnr = "12345678901",
                    narmesteLederFnr = "10987654321",
                    organisasjonsnummer = "999888777",
                )

                val persisted = repository.findForesporselForSykmeldt("12345678901")

                duplicateInsertId shouldBe null
                persisted.shouldHaveSize(1)
            }

            it("stores new request when nearest leader changes within grace period") {
                repository.storeIfNotRecentlyRequested(
                    sykmeldtFnr = "12345678901",
                    narmesteLederFnr = "10987654321",
                    organisasjonsnummer = "999888777",
                )

                val insertedId = repository.storeIfNotRecentlyRequested(
                    sykmeldtFnr = "12345678901",
                    narmesteLederFnr = "10987654322",
                    organisasjonsnummer = "999888777",
                )

                val persisted = repository.findForesporselForSykmeldt("12345678901")

                insertedId shouldBe persisted.first().id
                persisted.shouldHaveSize(2)
            }

            it("stores new request when previous request is outside grace period") {
                repository.storeIfNotRecentlyRequested(
                    sykmeldtFnr = "12345678901",
                    narmesteLederFnr = "10987654321",
                    organisasjonsnummer = "999888777",
                )
                setCreatedAtForAllRequests(
                    sykmeldtFnr = "12345678901",
                    createdAt = Instant.now().minusSeconds((FORESPORSEL_GRACE_PERIOD_DAYS + 1) * 24 * 60 * 60),
                )

                val insertedId = repository.storeIfNotRecentlyRequested(
                    sykmeldtFnr = "12345678901",
                    narmesteLederFnr = "10987654321",
                    organisasjonsnummer = "999888777",
                )

                val persisted = repository.findForesporselForSykmeldt("12345678901")

                insertedId shouldBe persisted.first().id
                persisted.shouldHaveSize(2)
            }
        }

        describe("findForesporselForSykmeldt") {
            it("returns only requests for requested sykmeldt") {
                repository.storeIfNotRecentlyRequested(
                    sykmeldtFnr = "12345678901",
                    narmesteLederFnr = "10987654321",
                    organisasjonsnummer = "999888777",
                )
                repository.storeIfNotRecentlyRequested(
                    sykmeldtFnr = "12345678902",
                    narmesteLederFnr = "10987654322",
                    organisasjonsnummer = "111222333",
                )

                val persisted = repository.findForesporselForSykmeldt("12345678901")

                persisted.shouldHaveSize(1)
                persisted.single().sykmeldtFnr shouldBe "12345678901"
                persisted.single().narmesteLederFnr shouldBe "10987654321"
                persisted.single().organisasjonsnummer shouldBe "999888777"
            }
        }
    })

private fun setCreatedAtForAllRequests(
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
