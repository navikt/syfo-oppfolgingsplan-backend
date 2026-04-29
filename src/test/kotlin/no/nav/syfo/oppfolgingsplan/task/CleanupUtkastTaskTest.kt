package no.nav.syfo.oppfolgingsplan.task

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService

class CleanupUtkastTaskTest :
    DescribeSpec({
        describe("runTask") {
            it("should continue next iteration after exception in cleanup") {
                val leaderElection = mockk<LeaderElection>()
                val oppfolgingsplanService = mockk<OppfolgingsplanService>()

                coEvery { leaderElection.isLeader() } returns true
                coEvery { oppfolgingsplanService.deleteExpiredOppfolgingsplanUtkast(any(), any(), any()) } throws RuntimeException("boom") andThen 0 andThen 0

                val cleanupUtkastTask = CleanupUtkastTask(
                    leaderElection = leaderElection,
                    oppfolgingsplanService = oppfolgingsplanService,
                    delayMillis = 10,
                )

                runBlocking {
                    val job = launch { cleanupUtkastTask.runTask() }

                    delay(35)
                    job.cancel()
                    job.join()
                }

                coVerify(atLeast = 2) {
                    oppfolgingsplanService.deleteExpiredOppfolgingsplanUtkast(any(), any(), any())
                }
            }

            it("should call cleanup with configured retention months and batch size") {
                val leaderElection = mockk<LeaderElection>()
                val oppfolgingsplanService = mockk<OppfolgingsplanService>()

                coEvery { leaderElection.isLeader() } returns true
                coEvery { oppfolgingsplanService.deleteExpiredOppfolgingsplanUtkast(any(), any(), any()) } returns 0

                val cleanupUtkastTask = CleanupUtkastTask(
                    leaderElection = leaderElection,
                    oppfolgingsplanService = oppfolgingsplanService,
                    delayMillis = 10,
                )

                runBlocking {
                    val job = launch { cleanupUtkastTask.runTask() }

                    delay(15)
                    job.cancel()
                    job.join()
                }

                coVerify(atLeast = 1) {
                    oppfolgingsplanService.deleteExpiredOppfolgingsplanUtkast(4, 100, null)
                }
            }
        }
    })
