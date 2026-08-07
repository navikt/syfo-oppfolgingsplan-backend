package no.nav.syfo.application.outbox

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainAll
import no.nav.syfo.TestDB

class OutboxSchemaTest :
    DescribeSpec({
        it("has the minimal outbox columns") {
            val columns = TestDB.database.connection.use { connection ->
                connection.metaData.getColumns(null, null, "outbox", null).use { resultSet ->
                    buildList {
                        while (resultSet.next()) add(resultSet.getString("COLUMN_NAME"))
                    }
                }
            }

            columns shouldContainAll listOf(
                "uuid",
                "message_type",
                "dedup_key",
                "external_ref",
                "payload",
                "scheduled_at",
                "status",
                "created_at",
                "sendt_at",
            )
        }
    })
