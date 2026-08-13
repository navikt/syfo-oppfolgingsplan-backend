package no.nav.syfo.application.outbox

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.TestDB
import no.nav.syfo.application.outbox.db.findOutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.milliseconds

class OutboxTaskTest :
    DescribeSpec({
        beforeTest { TestDB.clearAllData() }

        it("processes registered adapters on every replica without leader election") {
            val message = TestDB.database.enqueueTestOutboxMessage()
            val worker = OutboxWorker(
                database = TestDB.database,
                handlers = listOf(TestOutboxHandler()),
                clock = Clock.fixed(Instant.parse("2026-08-12T12:00:00Z"), ZoneOffset.UTC),
            )

            val taskJob = launch { OutboxTask(worker, 10.milliseconds).runTask() }
            delay(100)
            taskJob.cancelAndJoin()

            TestDB.database.findOutboxMessage(message)?.status shouldBe OutboxStatus.SENT
        }
    })
