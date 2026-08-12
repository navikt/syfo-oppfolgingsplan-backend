package no.nav.syfo.application.outbox

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.TestDB
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.application.outbox.db.findOutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class OutboxTaskTest :
    DescribeSpec({
        beforeTest { TestDB.clearAllData() }

        it("processes registered adapters when executed by the recurring task") {
            val message = TestDB.database.enqueueTestOutboxMessage()
            val leaderElection = mockk<LeaderElection>()
            coEvery { leaderElection.isLeader() } returns true
            val processor = OutboxProcessor(
                database = TestDB.database,
                handlers = listOf(TestOutboxHandler()),
                clock = Clock.fixed(Instant.parse("2026-08-12T12:00:00Z"), ZoneOffset.UTC),
            )

            OutboxTask(leaderElection, processor).execute()

            TestDB.database.findOutboxMessage(message.uuid)?.status shouldBe OutboxStatus.SENT
        }
    })
