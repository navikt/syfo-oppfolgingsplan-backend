package no.nav.syfo.oppfolgingsplan.task

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService

class CleanupUtkastTaskTest :
    DescribeSpec({
        describe("runCleanup") {
            it("should continue next iteration after exception in cleanup") {
                val leaderElection = mockk<LeaderElection>()
                val oppfolgingsplanService = mockk<OppfolgingsplanService>()

                coEvery { leaderElection.isLeader() } returns true
                coEvery {
                    oppfolgingsplanService.deleteExpiredOppfolgingsplanUtkast(any<Int>())
                } throws RuntimeException("boom") andThen 0 andThen 0

                val cleanupUtkastTask = CleanupUtkastTask(
                    leaderElection = leaderElection,
                    oppfolgingsplanService = oppfolgingsplanService,
                    delayMillis = 10,
                )

                runBlocking {
                    cleanupUtkastTask.runCleanup()
                    cleanupUtkastTask.runCleanup()
                }

                coVerify(exactly = 2) {
                    oppfolgingsplanService.deleteExpiredOppfolgingsplanUtkast(any<Int>())
                }
            }

            it("should call cleanup with configured retention months and batch size") {
                val leaderElection = mockk<LeaderElection>()
                val oppfolgingsplanService = mockk<OppfolgingsplanService>()

                coEvery { leaderElection.isLeader() } returns true
                coEvery {
                    oppfolgingsplanService.deleteExpiredOppfolgingsplanUtkast(any<Int>())
                } returns 0

                val cleanupUtkastTask = CleanupUtkastTask(
                    leaderElection = leaderElection,
                    oppfolgingsplanService = oppfolgingsplanService,
                    delayMillis = 10,
                )

                runBlocking {
                    cleanupUtkastTask.runCleanup()
                }

                coVerify(exactly = 1) {
                    oppfolgingsplanService.deleteExpiredOppfolgingsplanUtkast(4)
                }
            }
        }
    })
