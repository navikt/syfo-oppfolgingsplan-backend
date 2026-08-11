package no.nav.syfo.oppfolgingsplan.db

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import no.nav.syfo.TestDB

class UnntaksvurderingSchemaTest :
    DescribeSpec({
        describe("unntaksvurdering schema") {
            it("keeps Flyway and Exposed index definitions aligned") {
                val exposedIndexes = UnntaksvurderingTable.indices.map { index ->
                    IndexMetadata(
                        name = index.indexName,
                        columns = index.columns.map { it.name },
                        hasFilter = index.filterCondition != null,
                    )
                }

                val migratedIndexes = TestDB.database.connection.use { connection ->
                    connection.metaData.getIndexInfo(null, null, "unntaksvurdering", false, false).use { resultSet ->
                        buildList {
                            while (resultSet.next()) {
                                val indexName = resultSet.getString("INDEX_NAME") ?: continue
                                if (indexName == "unntaksvurdering_pkey") continue

                                add(
                                    IndexColumnMetadata(
                                        name = indexName,
                                        position = resultSet.getShort("ORDINAL_POSITION").toInt(),
                                        column = resultSet.getString("COLUMN_NAME"),
                                        hasFilter = resultSet.getString("FILTER_CONDITION") != null,
                                    ),
                                )
                            }
                        }
                    }
                }.groupBy { it.name }
                    .map { (name, columns) ->
                        IndexMetadata(
                            name = name,
                            columns = columns.sortedBy { it.position }.map { it.column },
                            hasFilter = columns.any { it.hasFilter },
                        )
                    }

                migratedIndexes.shouldContainExactlyInAnyOrder(exposedIndexes)
            }
        }
    })

private data class IndexMetadata(
    val name: String,
    val columns: List<String>,
    val hasFilter: Boolean,
)

private data class IndexColumnMetadata(
    val name: String,
    val position: Int,
    val column: String,
    val hasFilter: Boolean,
)
