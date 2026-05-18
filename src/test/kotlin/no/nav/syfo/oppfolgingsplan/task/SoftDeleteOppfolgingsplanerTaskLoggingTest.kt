package no.nav.syfo.oppfolgingsplan.task

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.oppfolgingsplan.api.v1.COUNT_OPPFOLGINGSPLAN_SOFT_DELETED
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.days

class SoftDeleteOppfolgingsplanerTaskLoggingTest :
    DescribeSpec({
        describe("execute") {
            it("should call service and increment counter") {
                val leaderElection = mockk<LeaderElection>()
                val oppfolgingsplanService = mockk<OppfolgingsplanService>()
                val counterBefore = COUNT_OPPFOLGINGSPLAN_SOFT_DELETED.count()

                coEvery { oppfolgingsplanService.softDeleteExpiredOppfolgingsplaner() } returns 3

                val task = SoftDeleteOppfolgingsplanerTask(
                    leaderElection = leaderElection,
                    oppfolgingsplanService = oppfolgingsplanService,
                    interval = 1.days,
                )

                task.execute()

                coVerify(exactly = 1) { oppfolgingsplanService.softDeleteExpiredOppfolgingsplaner() }
                COUNT_OPPFOLGINGSPLAN_SOFT_DELETED.count() - counterBefore shouldBe 3.0
            }

            it("should log start and zero-result on info when no planer are soft-deleted") {
                val leaderElection = mockk<LeaderElection>()
                val oppfolgingsplanService = mockk<OppfolgingsplanService>()
                val logger = LoggerFactory.getLogger(SoftDeleteOppfolgingsplanerTask::class.qualifiedName) as Logger
                val appender = ListAppender<ILoggingEvent>().apply { start() }

                coEvery { oppfolgingsplanService.softDeleteExpiredOppfolgingsplaner() } returns 0
                val originalLevel = logger.level
                logger.level = Level.INFO
                logger.addAppender(appender)

                try {
                    val task = SoftDeleteOppfolgingsplanerTask(
                        leaderElection = leaderElection,
                        oppfolgingsplanService = oppfolgingsplanService,
                        interval = 1.days,
                    )

                    task.execute()

                    appender.list.any {
                        it.level == Level.INFO && it.formattedMessage == "Starting task for soft-delete expired oppfolgingsplaner"
                    } shouldBe true
                    appender.list.any {
                        it.level == Level.INFO && it.formattedMessage == "Found 0 expired oppfolgingsplaner to soft-delete"
                    } shouldBe true
                } finally {
                    logger.level = originalLevel
                    logger.detachAppender(appender)
                    appender.stop()
                }
            }
        }
    })
