package no.nav.syfo.oppfolgingsplan.service

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.varsel.EsyfovarselProducer
import org.slf4j.LoggerFactory

class OppfolgingsplanServiceSafeguardTest :
    DescribeSpec({
        val logger = LoggerFactory.getLogger(OppfolgingsplanService::class.qualifiedName) as Logger
        val service = OppfolgingsplanService(
            database = mockk<DatabaseInterface>(relaxed = true),
            pdlService = mockk(relaxed = true),
            esyfovarselProducer = mockk<EsyfovarselProducer>(relaxed = true),
            budstikkaPublisher = mockk(relaxed = true),
            aaregService = mockk(relaxed = true),
        )

        describe("runSoftDeleteExpiredOppfolgingsplanerLoop") {
            it("stops when safeguard limit is reached and logs warning without PII") {
                val appender = ListAppender<ILoggingEvent>().apply { start() }
                val originalLevel = logger.level
                logger.level = Level.WARN
                logger.addAppender(appender)

                try {
                    val totalSoftDeleted = service.runSoftDeleteExpiredOppfolgingsplanerLoop(
                        maxBatchIterations = 3,
                    ) {
                        1
                    }

                    totalSoftDeleted shouldBe 3
                    appender.list.any {
                        it.level == Level.WARN &&
                            it.formattedMessage == "Stopped soft-delete loop after reaching safeguard of 3 batches; total soft-deleted so far: 3"
                    } shouldBe true
                } finally {
                    logger.level = originalLevel
                    logger.detachAppender(appender)
                    appender.stop()
                }
            }

            it("keeps processing until batch returns zero before safeguard limit") {
                var invocations = 0

                val totalSoftDeleted = service.runSoftDeleteExpiredOppfolgingsplanerLoop(
                    maxBatchIterations = 5,
                ) {
                    invocations++
                    when (invocations) {
                        1 -> 2
                        2 -> 1
                        else -> 0
                    }
                }

                totalSoftDeleted shouldBe 3
                invocations shouldBe 3
            }
        }
    })
