package no.nav.syfo.application.outbox

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import no.nav.syfo.TestDB
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import no.nav.syfo.application.outbox.domain.OutboxRelevans
import no.nav.syfo.application.outbox.domain.OutboxStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class OutboxProcessorTest :
    DescribeSpec({
        val database = TestDB.database
        val clock = Clock.fixed(Instant.parse("2025-06-25T07:00:00Z"), ZoneOffset.UTC)

        beforeTest { TestDB.clearAllData() }

        it("sends and marks a relevant row") {
            val message = database.addOutboxMessage()
            val handler = FakeOutboxHandler()

            testProcessor(listOf(handler), clock, database).processReadyMessages(
                OutboxMessageType.PAAMINNELSE_OPPFOLGINGSPLAN,
            )

            handler.sendteMeldinger shouldContainExactly listOf(message.uuid)
            database.getOutboxMessage(message.uuid).shouldNotBeNull().status shouldBe OutboxStatus.SENDT
        }

        it("marks an irrelevant row without sending") {
            val message = database.addOutboxMessage()
            val handler = FakeOutboxHandler(relevant = { OutboxRelevans.IkkeRelevant })

            testProcessor(listOf(handler), clock, database).processReadyMessages(
                OutboxMessageType.PAAMINNELSE_OPPFOLGINGSPLAN,
            )

            handler.sendteMeldinger.size shouldBe 0
            database.getOutboxMessage(message.uuid).shouldNotBeNull().status shouldBe OutboxStatus.IKKE_RELEVANT
        }

        it("leaves a deferred row ready and stops the batch") {
            val deferred = database.addOutboxMessage(dedupKey = "deferred")
            val next = database.addOutboxMessage(dedupKey = "next")
            val handler = FakeOutboxHandler(relevant = { OutboxRelevans.Utsatt })

            testProcessor(listOf(handler), clock, database).processReadyMessages(
                OutboxMessageType.PAAMINNELSE_OPPFOLGINGSPLAN,
            )

            database.getOutboxMessage(deferred.uuid).shouldNotBeNull().status shouldBe OutboxStatus.KLAR
            database.getOutboxMessage(next.uuid).shouldNotBeNull().status shouldBe OutboxStatus.KLAR
        }

        it("does not process a row before its scheduled time") {
            val message = database.addOutboxMessage(
                scheduledAt = Instant.parse("2025-06-25T07:01:00Z"),
            )
            val handler = FakeOutboxHandler()

            testProcessor(listOf(handler), clock, database).processReadyMessages(
                OutboxMessageType.PAAMINNELSE_OPPFOLGINGSPLAN,
            )

            handler.sendteMeldinger.size shouldBe 0
            database.getOutboxMessage(message.uuid).shouldNotBeNull().status shouldBe OutboxStatus.KLAR
        }

        it("rolls back a failed delivery for retry on the next task run") {
            val message = database.addOutboxMessage()
            val handler = FakeOutboxHandler(onSend = { error("Kafka unavailable") })

            shouldThrow<IllegalStateException> {
                testProcessor(listOf(handler), clock, database).processReadyMessages(
                    OutboxMessageType.PAAMINNELSE_OPPFOLGINGSPLAN,
                )
            }

            database.getOutboxMessage(message.uuid).shouldNotBeNull().status shouldBe OutboxStatus.KLAR
        }

        it("commits each row before processing the next") {
            val first = database.addOutboxMessage(dedupKey = "first")
            val second = database.addOutboxMessage(dedupKey = "second")
            var sends = 0
            val handler = FakeOutboxHandler(
                onSend = {
                    sends++
                    if (sends == 2) error("second send fails")
                },
            )

            shouldThrow<IllegalStateException> {
                testProcessor(listOf(handler), clock, database).processReadyMessages(
                    OutboxMessageType.PAAMINNELSE_OPPFOLGINGSPLAN,
                )
            }

            database.getOutboxMessage(first.uuid).shouldNotBeNull().status shouldBe OutboxStatus.SENDT
            database.getOutboxMessage(second.uuid).shouldNotBeNull().status shouldBe OutboxStatus.KLAR
        }

        it("propagates cancellation without updating the row") {
            val message = database.addOutboxMessage()
            val handler = FakeOutboxHandler(onSend = { throw CancellationException() })

            shouldThrow<CancellationException> {
                testProcessor(listOf(handler), clock, database).processReadyMessages(
                    OutboxMessageType.PAAMINNELSE_OPPFOLGINGSPLAN,
                )
            }

            database.getOutboxMessage(message.uuid).shouldNotBeNull().status shouldBe OutboxStatus.KLAR
        }
    })
