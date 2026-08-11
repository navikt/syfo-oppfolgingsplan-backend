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
import no.nav.syfo.oppfolgingsplan.api.v1.COUNT_UNNTAKSVURDERING_SOFT_DELETED
import no.nav.syfo.oppfolgingsplan.service.UnntaksvurderingService
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class SoftDeleteUnntaksvurderingerTaskTest :
    DescribeSpec({
        describe("intervalForEnvironment") {
            it("uses one day in production and five minutes otherwise") {
                SoftDeleteUnntaksvurderingerTask.intervalForEnvironment(isProdEnv = true) shouldBe 1.days
                SoftDeleteUnntaksvurderingerTask.intervalForEnvironment(isProdEnv = false) shouldBe 5.minutes
            }
        }

        describe("execute") {
            it("calls service and increments counter") {
                val service = mockk<UnntaksvurderingService>()
                val counterBefore = COUNT_UNNTAKSVURDERING_SOFT_DELETED.count()
                coEvery { service.softDeleteExpiredUnntaksvurderinger() } returns 3

                SoftDeleteUnntaksvurderingerTask(
                    leaderElection = mockk<LeaderElection>(),
                    unntaksvurderingService = service,
                ).execute()

                coVerify(exactly = 1) { service.softDeleteExpiredUnntaksvurderinger() }
                COUNT_UNNTAKSVURDERING_SOFT_DELETED.count() - counterBefore shouldBe 3.0
            }

            it("logs start and zero-result") {
                val service = mockk<UnntaksvurderingService>()
                val logger = LoggerFactory.getLogger(SoftDeleteUnntaksvurderingerTask::class.qualifiedName) as Logger
                val appender = ListAppender<ILoggingEvent>().apply { start() }
                val originalLevel = logger.level
                coEvery { service.softDeleteExpiredUnntaksvurderinger() } returns 0
                logger.level = Level.INFO
                logger.addAppender(appender)

                try {
                    SoftDeleteUnntaksvurderingerTask(
                        leaderElection = mockk<LeaderElection>(),
                        unntaksvurderingService = service,
                    ).execute()

                    appender.list.any {
                        it.level == Level.INFO &&
                            it.formattedMessage == "Starting task for soft-delete expired unntaksvurderinger"
                    } shouldBe true
                    appender.list.any {
                        it.level == Level.INFO &&
                            it.formattedMessage == "Found 0 expired unntaksvurderinger to soft-delete"
                    } shouldBe true
                } finally {
                    logger.level = originalLevel
                    logger.detachAppender(appender)
                    appender.stop()
                }
            }
        }
    })
