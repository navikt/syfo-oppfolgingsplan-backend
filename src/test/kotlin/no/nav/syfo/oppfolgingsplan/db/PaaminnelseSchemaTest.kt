package no.nav.syfo.oppfolgingsplan.db

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import no.nav.syfo.TestDB

class PaaminnelseSchemaTest :
    DescribeSpec({
        describe("paaminnelse schema") {
            it("adds forlop_fom to paaminnelse") {
                val paaminnelseColumns = TestDB.database.connection.use { connection ->
                    connection.metaData.getColumns(null, null, "paaminnelse", null).use { resultSet ->
                        buildList {
                            while (resultSet.next()) {
                                add(resultSet.getString("COLUMN_NAME"))
                            }
                        }
                    }
                }

                paaminnelseColumns shouldContainAll listOf(
                    "uuid",
                    "organisasjonsnummer",
                    "sykmeldt_fnr",
                    "forlop_fom",
                    "bestilt",
                )
            }

            it("requires forlop_fom") {
                val forlopFomNullable = TestDB.database.connection.use { connection ->
                    connection.metaData.getColumns(null, null, "paaminnelse", "forlop_fom").use { resultSet ->
                        resultSet.next()
                        resultSet.getString("IS_NULLABLE")
                    }
                }

                forlopFomNullable shouldBe "NO"
            }
        }
    })
