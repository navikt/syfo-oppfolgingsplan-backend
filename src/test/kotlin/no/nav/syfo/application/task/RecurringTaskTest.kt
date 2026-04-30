package no.nav.syfo.application.task

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.leaderelection.LeaderElection
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class RecurringTaskTest :
    DescribeSpec({
        describe("runTask") {
            it("should call execute when pod is leader") {
                val leaderElection = mockk<LeaderElection>()
                coEvery { leaderElection.isLeader() } returns true

                val executeCount = AtomicInteger(0)
                val task = object : RecurringTask("test-task", 10.milliseconds, leaderElection) {
                    override suspend fun execute() {
                        executeCount.incrementAndGet()
                    }
                }

                val job = launch { task.runTask() }
                delay(100)
                job.cancelAndJoin()

                executeCount.get() shouldBeGreaterThan 0
            }

            it("should skip execute when pod is not leader") {
                val leaderElection = mockk<LeaderElection>()
                coEvery { leaderElection.isLeader() } returns false

                val executeCount = AtomicInteger(0)
                val task = object : RecurringTask("test-task", 10.milliseconds, leaderElection) {
                    override suspend fun execute() {
                        executeCount.incrementAndGet()
                    }
                }

                val job = launch { task.runTask() }
                delay(100)
                job.cancelAndJoin()

                executeCount.get() shouldBe 0
            }

            it("should continue loop after exception in execute") {
                val leaderElection = mockk<LeaderElection>()
                coEvery { leaderElection.isLeader() } returns true

                val executeCount = AtomicInteger(0)
                val task = object : RecurringTask("test-task", 10.milliseconds, leaderElection) {
                    override suspend fun execute() {
                        val callNumber = executeCount.incrementAndGet()
                        if (callNumber == 1) {
                            throw RuntimeException("boom")
                        }
                    }
                }

                val job = launch { task.runTask() }
                delay(100)
                job.cancelAndJoin()

                executeCount.get() shouldBeGreaterThan 1
            }

            it("should stop loop when execute throws CancellationException") {
                val leaderElection = mockk<LeaderElection>()
                coEvery { leaderElection.isLeader() } returns true

                val executeCount = AtomicInteger(0)
                val task = object : RecurringTask("test-task", 10.milliseconds, leaderElection) {
                    override suspend fun execute() {
                        executeCount.incrementAndGet()
                        throw CancellationException("cancelled internally")
                    }
                }

                val job = launch { task.runTask() }
                delay(100)
                job.cancelAndJoin()

                executeCount.get() shouldBe 1
            }

            it("should stop gracefully when job is cancelled") {
                val leaderElection = mockk<LeaderElection>()
                coEvery { leaderElection.isLeader() } returns true

                val task = object : RecurringTask("test-task", 10.milliseconds, leaderElection) {
                    override suspend fun execute() {
                        // no-op
                    }
                }

                val job = launch { task.runTask() }
                delay(50)
                job.cancelAndJoin()
                // If we reach here, the task stopped without leaking exceptions
            }
        }
    })
