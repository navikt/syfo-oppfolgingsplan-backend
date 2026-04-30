package no.nav.syfo.oppfolgingsplan.task

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import kotlin.time.Duration.Companion.days

class CleanupUtkastTaskTest :
    DescribeSpec({
        describe("execute") {
            it("should delete expired drafts and increment counter") {
                val leaderElection = mockk<LeaderElection>()
                val oppfolgingsplanService = mockk<OppfolgingsplanService>()

                coEvery { leaderElection.isLeader() } returns true
                coEvery {
                    oppfolgingsplanService.deleteExpiredOppfolgingsplanUtkast(any<Int>())
                } returns 3

                val task = CleanupUtkastTask(
                    leaderElection = leaderElection,
                    oppfolgingsplanService = oppfolgingsplanService,
                    interval = 1.days,
                )

                task.execute()

                coVerify(exactly = 1) {
                    oppfolgingsplanService.deleteExpiredOppfolgingsplanUtkast(4)
                }
            }

            it("should call cleanup with default retention months") {
                val leaderElection = mockk<LeaderElection>()
                val oppfolgingsplanService = mockk<OppfolgingsplanService>()

                coEvery { leaderElection.isLeader() } returns true
                coEvery {
                    oppfolgingsplanService.deleteExpiredOppfolgingsplanUtkast(any<Int>())
                } returns 0

                val task = CleanupUtkastTask(
                    leaderElection = leaderElection,
                    oppfolgingsplanService = oppfolgingsplanService,
                    interval = 1.days,
                )

                task.execute()

                coVerify(exactly = 1) {
                    oppfolgingsplanService.deleteExpiredOppfolgingsplanUtkast(4)
                }
            }
        }
    })
