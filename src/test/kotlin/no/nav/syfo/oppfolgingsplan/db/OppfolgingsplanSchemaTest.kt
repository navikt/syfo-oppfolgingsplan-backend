package no.nav.syfo.oppfolgingsplan.db

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.syfo.TestDB

class OppfolgingsplanSchemaTest :
    DescribeSpec({
        describe("oppfolgingsplan schema") {
            it("should add evaluering paaminnelse columns only on oppfolgingsplan") {
                val oppfolgingsplanColumns = TestDB.database.connection.use { connection ->
                    connection.metaData.getColumns(null, null, "oppfolgingsplan", null).use { resultSet ->
                        buildList {
                            while (resultSet.next()) {
                                add(
                                    ColumnMetadata(
                                        name = resultSet.getString("COLUMN_NAME"),
                                        isNullable = resultSet.getString("IS_NULLABLE") == "YES",
                                        defaultValue = resultSet.getString("COLUMN_DEF"),
                                    ),
                                )
                            }
                        }
                    }
                }
                val utkastColumns = TestDB.database.connection.use { connection ->
                    connection.metaData.getColumns(null, null, "oppfolgingsplan_utkast", null).use { resultSet ->
                        buildList {
                            while (resultSet.next()) {
                                add(resultSet.getString("COLUMN_NAME"))
                            }
                        }
                    }
                }

                oppfolgingsplanColumns.map { it.name }.filter { it.startsWith("evaluering_paaminnelse") }
                    .shouldContainExactlyInAnyOrder(
                        "evaluering_paaminnelse",
                        "evaluering_paaminnelse_outbox_at",
                    )
                utkastColumns shouldNotContain "evaluering_paaminnelse"
                utkastColumns shouldNotContain "evaluering_paaminnelse_outbox_at"

                oppfolgingsplanColumns.find { it.name == "evaluering_paaminnelse" }.shouldNotBeNull().apply {
                    isNullable shouldBe false
                    defaultValue shouldBe "false"
                }

                oppfolgingsplanColumns.find { it.name == "evaluering_paaminnelse_outbox_at" }.shouldNotBeNull().apply {
                    isNullable shouldBe true
                }
            }
        }
    })

private data class ColumnMetadata(
    val name: String,
    val isNullable: Boolean,
    val defaultValue: String?,
)
