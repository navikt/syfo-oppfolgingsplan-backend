package no.nav.syfo.oppfolgingsplan.service

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy
import ch.qos.logback.core.read.ListAppender
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.TestDB
import no.nav.syfo.defaultSykmeldt
import no.nav.syfo.pdl.PdlService
import org.slf4j.LoggerFactory

const val SYNTHETIC_SYKMELDT_FNR = "00000000000"
const val SYNTHETIC_NARMESTE_LEDER_FNR = "11111111111"
const val SYNTHETIC_NARMESTE_LEDER_NAME = "SYNTHETIC_NARMESTE_LEDER_NAME"

private fun IThrowableProxy.allMessages(): Sequence<String> = sequence {
    message?.let { yield(it) }
    if (!isCyclic) {
        suppressed.forEach { yieldAll(it.allMessages()) }
        cause?.let { yieldAll(it.allMessages()) }
    }
}

fun Iterable<ILoggingEvent>.shouldNotContainSensitiveUnntaksvurderingData(
    sensitiveValues: Collection<String>,
) {
    val fnrPattern = Regex("(?<!\\d)\\d{11}(?!\\d)")

    forEach { event ->
        val loggedValues = buildList {
            add(event.formattedMessage)
            event.throwableProxy?.allMessages()?.let(::addAll)
            addAll(event.mdcPropertyMap.values)
        }

        loggedValues.forEach { value ->
            sensitiveValues.none(value::contains) shouldBe true
            fnrPattern.containsMatchIn(value) shouldBe false
        }
    }
}

class UnntaksvurderingLoggingTest :
    DescribeSpec({
        val testDb = TestDB.database
        val pdlService = mockk<PdlService>()
        val service = UnntaksvurderingService(testDb, pdlService)
        val logger = LoggerFactory.getLogger(UnntaksvurderingService::class.qualifiedName) as Logger

        beforeTest {
            TestDB.clearAllData()
        }

        describe("createUnntaksvurdering") {
            it("does not log synthetic identifiers or nearest leader name on success") {
                val appender = ListAppender<ILoggingEvent>().apply { start() }
                val originalLevel = logger.level
                val sykmeldt = defaultSykmeldt().copy(fnr = SYNTHETIC_SYKMELDT_FNR)
                coEvery { pdlService.getNameFor(SYNTHETIC_NARMESTE_LEDER_FNR) } returns SYNTHETIC_NARMESTE_LEDER_NAME
                logger.level = Level.INFO
                logger.addAppender(appender)

                try {
                    service.createUnntaksvurdering(SYNTHETIC_NARMESTE_LEDER_FNR, sykmeldt)

                    appender.list.shouldNotContainSensitiveUnntaksvurderingData(
                        listOf(
                            SYNTHETIC_SYKMELDT_FNR,
                            SYNTHETIC_NARMESTE_LEDER_FNR,
                            SYNTHETIC_NARMESTE_LEDER_NAME,
                        ),
                    )
                } finally {
                    logger.level = originalLevel
                    logger.detachAppender(appender)
                    appender.stop()
                }
            }

            it("detects synthetic identifiers in nested exception causes") {
                val appender = ListAppender<ILoggingEvent>().apply { start() }
                logger.addAppender(appender)

                try {
                    logger.error(
                        "Test exception",
                        IllegalStateException(
                            "Outer exception",
                            IllegalArgumentException(SYNTHETIC_NARMESTE_LEDER_FNR),
                        ),
                    )

                    shouldThrow<AssertionError> {
                        appender.list.shouldNotContainSensitiveUnntaksvurderingData(
                            listOf(SYNTHETIC_NARMESTE_LEDER_FNR),
                        )
                    }
                } finally {
                    logger.detachAppender(appender)
                    appender.stop()
                }
            }
        }
    })
