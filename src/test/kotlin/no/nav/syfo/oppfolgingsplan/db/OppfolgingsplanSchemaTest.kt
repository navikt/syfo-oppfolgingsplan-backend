package no.nav.syfo.oppfolgingsplan.db

import io.kotest.assertions.withClue
import io.kotest.core.annotation.Isolate
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.syfo.TestDB
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils

@Isolate
class OppfolgingsplanSchemaTest :
    DescribeSpec({
        describe("oppfolgingsplan schema") {
            it("keeps the Exposed mappings aligned with the Flyway schema") {
                val drift = transaction(TestDB.database.exposedDatabase) {
                    MigrationUtils.statementsRequiredForDatabaseMigration(
                        OppfolgingsplanTable,
                        OppfolgingsplanUtkastTable,
                        withLogs = false,
                    )
                }

                withClue("Oppfolgingsplan schema drift:\n${drift.joinToString("\n")}") {
                    drift.shouldBeEmpty()
                }
            }

            it("keeps the visible lookup index ordered by newest plan") {
                val indexDefinition = TestDB.database.connection.use { connection ->
                    connection.prepareStatement(
                        "SELECT pg_get_indexdef('idx_oppfolgingsplan_visible_lookup'::regclass)",
                    ).use { statement ->
                        statement.executeQuery().use { resultSet ->
                            resultSet.next()
                            resultSet.getString(1)
                        }
                    }
                }

                indexDefinition shouldBe
                    "CREATE INDEX idx_oppfolgingsplan_visible_lookup ON public.oppfolgingsplan USING btree " +
                    "(sykmeldt_fnr, organisasjonsnummer, created_at DESC) WHERE (skjult_fra IS NULL)"
            }

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
