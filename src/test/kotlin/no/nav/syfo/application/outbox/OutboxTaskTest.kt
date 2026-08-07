package no.nav.syfo.application.outbox

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.TestDB
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.application.outbox.domain.OutboxStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class OutboxTaskTest :
    DescribeSpec({
        val clock = Clock.fixed(Instant.parse("2025-06-25T07:00:00Z"), ZoneOffset.UTC)

        beforeTest { TestDB.clearAllData() }

        it("sends ready messages end to end") {
            val leaderElection = mockk<LeaderElection>()
            coEvery { leaderElection.isLeader() } returns true
            val message = TestDB.database.addOutboxMessage()
            val handler = FakeOutboxHandler()

            OutboxTask(leaderElection, testProcessor(listOf(handler), clock, TestDB.database)).execute()

            handler.sendteMeldinger.size shouldBe 1
            TestDB.database.getOutboxMessage(message.uuid).shouldNotBeNull().status shouldBe OutboxStatus.SENDT
        }
    })
