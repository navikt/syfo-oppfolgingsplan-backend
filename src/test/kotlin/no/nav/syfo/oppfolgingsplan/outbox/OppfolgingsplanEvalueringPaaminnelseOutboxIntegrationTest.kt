package no.nav.syfo.oppfolgingsplan.outbox

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import no.nav.syfo.TestDB
import no.nav.syfo.aareg.AaregService
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.exposedTransaction
import no.nav.syfo.application.metric.METRICS_REGISTRY
import no.nav.syfo.application.outbox.MicrometerOutboxLifecycleMetrics
import no.nav.syfo.application.outbox.OUTBOX_ENQUEUED
import no.nav.syfo.application.outbox.db.claimOutboxMessages
import no.nav.syfo.application.outbox.db.enqueueOutboxMessage
import no.nav.syfo.application.outbox.db.findOutboxMessage
import no.nav.syfo.application.outbox.domain.NewOutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxCancellationReason
import no.nav.syfo.application.outbox.domain.OutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxStatus
import no.nav.syfo.defaultOppfolgingsplan
import no.nav.syfo.defaultPersistedOppfolgingsplanUtkast
import no.nav.syfo.defaultSykmeldt
import no.nav.syfo.dinesykmeldte.client.DineSykmeldteSykmelding
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.findOppfolgingsplanUtkastByNarmesteLederId
import no.nav.syfo.oppfolgingsplan.db.OppfolgingsplanFinalizationRepository
import no.nav.syfo.oppfolgingsplan.db.findAllOppfolgingsplanerBy
import no.nav.syfo.oppfolgingsplan.db.findOppfolgingsplanBy
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.oppfolgingsplan.service.UnntaksvurderingService
import no.nav.syfo.pdl.PdlService
import no.nav.syfo.persistOppfolgingsplanUtkast
import no.nav.syfo.varsel.EsyfovarselProducer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

class OppfolgingsplanEvalueringPaaminnelseOutboxIntegrationTest :
    DescribeSpec({
        val oslo = ZoneId.of("Europe/Oslo")
        val now = Instant.parse("2030-08-13T12:00:00Z")

        beforeTest {
            TestDB.clearAllData()
        }

        describe("evaluation reminder outbox rows on finalized plans") {
            it("creates exactly two READY rows with independent uuids when evalueringPaaminnelse=true") {
                val sykmeldt = defaultSykmeldt()
                val evalueringsdato = LocalDate.of(2030, 1, 15)
                val planUuid = TestDB.database.createOppfolgingsplan(
                    sykmeldt = sykmeldt,
                    evalueringPaaminnelse = true,
                    evalueringsdato = evalueringsdato,
                )

                val arbeidsgiverMessage = TestDB.database.findEvalueringMessage(
                    OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_MIN_SIDE_ARBEIDSGIVER,
                    planUuid,
                )
                val sykmeldtMessage = TestDB.database.findEvalueringMessage(
                    OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_DINE_SYKMELDTE,
                    planUuid,
                )
                val expectedAvailableAt = evalueringsdato.minusDays(3).atTime(9, 0).atZone(oslo).toInstant()

                arbeidsgiverMessage.status shouldBe OutboxStatus.READY
                sykmeldtMessage.status shouldBe OutboxStatus.READY
                arbeidsgiverMessage.availableAt shouldBe expectedAvailableAt
                sykmeldtMessage.availableAt shouldBe expectedAvailableAt
                (arbeidsgiverMessage.uuid == sykmeldtMessage.uuid) shouldBe false

                OppfolgingsplanOutboxMessageType.evalueringPaaminnelseTypes.forEach { messageType ->
                    val message = TestDB.database.findEvalueringMessage(messageType, planUuid)
                    message.dedupKey shouldBe planUuid.toString()
                    message.externalRef shouldBe planUuid.toString()
                    message.payload shouldBe "{}"
                }
            }

            it("creates no evaluation reminder rows when evalueringPaaminnelse=false") {
                val planUuid = TestDB.database.createOppfolgingsplan(
                    evalueringPaaminnelse = false,
                    evalueringsdato = LocalDate.of(2026, 1, 15),
                )

                OppfolgingsplanOutboxMessageType.evalueringPaaminnelseTypes.forEach { messageType ->
                    TestDB.database.findOutboxMessage(messageType, planUuid.toString()).shouldBeNull()
                }
            }

            it("uses Europe/Oslo winter scheduling at 09:00") {
                val evalueringsdato = LocalDate.of(2030, 1, 15)
                val planUuid = TestDB.database.createOppfolgingsplan(
                    evalueringPaaminnelse = true,
                    evalueringsdato = evalueringsdato,
                )
                val expectedAvailableAt = evalueringsdato.minusDays(3).atTime(9, 0).atZone(oslo).toInstant()

                OppfolgingsplanOutboxMessageType.evalueringPaaminnelseTypes.forEach { messageType ->
                    TestDB.database.findEvalueringMessage(messageType, planUuid).availableAt shouldBe expectedAvailableAt
                }
            }

            it("uses calendar-based DST scheduling at 09:00 Europe/Oslo") {
                val evalueringsdato = LocalDate.of(2032, 4, 1)
                val planUuid = TestDB.database.createOppfolgingsplan(
                    evalueringPaaminnelse = true,
                    evalueringsdato = evalueringsdato,
                )
                val expectedAvailableAt = Instant.parse("2032-03-29T07:00:00Z")

                OppfolgingsplanOutboxMessageType.evalueringPaaminnelseTypes.forEach { messageType ->
                    TestDB.database.findEvalueringMessage(messageType, planUuid).availableAt shouldBe expectedAvailableAt
                }
            }

            it("keeps past nominal schedules unchanged and immediately claimable") {
                val evalueringsdato = LocalDate.of(2020, 1, 10)
                val planUuid = TestDB.database.createOppfolgingsplan(
                    evalueringPaaminnelse = true,
                    evalueringsdato = evalueringsdato,
                )
                val plan = TestDB.database.findOppfolgingsplanBy(planUuid).shouldNotBeNull()
                val message = TestDB.database.findEvalueringMessage(
                    OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_DINE_SYKMELDTE,
                    planUuid,
                )
                val expectedAvailableAt = Instant.parse("2020-01-07T08:00:00Z")

                message.status shouldBe OutboxStatus.READY
                message.availableAt shouldBe expectedAvailableAt
                message.availableAt.isBefore(plan.createdAt) shouldBe true

                val claimedMessage = TestDB.database.exposedTransaction {
                    claimOutboxMessages(
                        messageType = message.messageType,
                        now = plan.createdAt,
                        limit = 1,
                        leaseDuration = 5.minutes,
                    ).single()
                }
                claimedMessage.uuid shouldBe message.uuid
                claimedMessage.status shouldBe OutboxStatus.CLAIMED
            }

            it("deduplicates retries for the same plan and channel") {
                val planUuid = TestDB.database.createOppfolgingsplan(
                    evalueringPaaminnelse = true,
                    evalueringsdato = LocalDate.of(2026, 4, 15),
                )
                val messageType = OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_MIN_SIDE_ARBEIDSGIVER
                val originalMessage = TestDB.database.findEvalueringMessage(messageType, planUuid)
                val insertedAgain = TestDB.database.exposedTransaction {
                    enqueueOutboxMessage(
                        NewOutboxMessage(
                            messageType = messageType,
                            dedupKey = planUuid.toString(),
                            externalRef = planUuid.toString(),
                            payload = "{}",
                            availableAt = originalMessage.availableAt,
                        ),
                    )
                }

                insertedAgain shouldBe false
                TestDB.database.findEvalueringMessage(messageType, planUuid).uuid shouldBe originalMessage.uuid
            }
        }

        describe("superseding cancellation") {
            it("cancels both older channels on finalized replacement even when leader changes and opt-out is false") {
                val sykmeldt = defaultSykmeldt()
                val oldPlanUuid = TestDB.database.createOppfolgingsplan(
                    sykmeldt = sykmeldt.copy(narmestelederId = "leader-old"),
                    evalueringPaaminnelse = true,
                    evalueringsdato = LocalDate.of(2026, 5, 15),
                )
                val newPlanUuid = TestDB.database.createOppfolgingsplan(
                    sykmeldt = sykmeldt.copy(narmestelederId = "leader-new"),
                    evalueringPaaminnelse = false,
                    evalueringsdato = LocalDate.of(2026, 6, 15),
                )

                OppfolgingsplanOutboxMessageType.evalueringPaaminnelseTypes.forEach { messageType ->
                    TestDB.database.findEvalueringMessage(messageType, oldPlanUuid).apply {
                        status shouldBe OutboxStatus.CANCELLED
                        cancellationReason shouldBe OutboxCancellationReason.SUPERSEDED
                        completedAt.shouldNotBeNull()
                    }
                    TestDB.database.findOutboxMessage(messageType, newPlanUuid.toString()).shouldBeNull()
                }
            }

            it("does not cancel reminders for different organizations") {
                val fnr = "12345678901"
                val planInOrgA = TestDB.database.createOppfolgingsplan(
                    sykmeldt = sykmeldt(
                        fnr = fnr,
                        orgnummer = "111111111",
                        narmesteLederId = "leader-a",
                        organisasjonsnavn = "Org A",
                    ),
                    evalueringPaaminnelse = true,
                    evalueringsdato = LocalDate.of(2026, 7, 15),
                )
                val planInOrgB = TestDB.database.createOppfolgingsplan(
                    sykmeldt = sykmeldt(
                        fnr = fnr,
                        orgnummer = "222222222",
                        narmesteLederId = "leader-b",
                        organisasjonsnavn = "Org B",
                    ),
                    evalueringPaaminnelse = true,
                    evalueringsdato = LocalDate.of(2026, 8, 15),
                )

                TestDB.database.createOppfolgingsplan(
                    sykmeldt = sykmeldt(
                        fnr = fnr,
                        orgnummer = "111111111",
                        narmesteLederId = "leader-c",
                        organisasjonsnavn = "Org A",
                    ),
                    evalueringPaaminnelse = false,
                    evalueringsdato = LocalDate.of(2026, 9, 15),
                )

                OppfolgingsplanOutboxMessageType.evalueringPaaminnelseTypes.forEach { messageType ->
                    TestDB.database.findEvalueringMessage(messageType, planInOrgA).status shouldBe OutboxStatus.CANCELLED
                    TestDB.database.findEvalueringMessage(messageType, planInOrgB).status shouldBe OutboxStatus.READY
                }
            }

            it("does not cancel reminders for another sykmeldt in the same organization") {
                val organisasjonsnummer = "333333333"
                val planForSykmeldtA = TestDB.database.createOppfolgingsplan(
                    sykmeldt = sykmeldt(
                        fnr = "11111111111",
                        orgnummer = organisasjonsnummer,
                        narmesteLederId = "leader-a1",
                        organisasjonsnavn = "Org C",
                    ),
                    evalueringPaaminnelse = true,
                    evalueringsdato = LocalDate.of(2026, 8, 15),
                )
                val planForSykmeldtB = TestDB.database.createOppfolgingsplan(
                    sykmeldt = sykmeldt(
                        fnr = "22222222222",
                        orgnummer = organisasjonsnummer,
                        narmesteLederId = "leader-b1",
                        organisasjonsnavn = "Org C",
                    ),
                    evalueringPaaminnelse = true,
                    evalueringsdato = LocalDate.of(2026, 9, 15),
                )

                TestDB.database.createOppfolgingsplan(
                    sykmeldt = sykmeldt(
                        fnr = "11111111111",
                        orgnummer = organisasjonsnummer,
                        narmesteLederId = "leader-a2",
                        organisasjonsnavn = "Org C",
                    ),
                    evalueringPaaminnelse = false,
                    evalueringsdato = LocalDate.of(2026, 10, 15),
                )

                OppfolgingsplanOutboxMessageType.evalueringPaaminnelseTypes.forEach { messageType ->
                    TestDB.database.findEvalueringMessage(messageType, planForSykmeldtA).status shouldBe OutboxStatus.CANCELLED
                    TestDB.database.findEvalueringMessage(messageType, planForSykmeldtB).status shouldBe OutboxStatus.READY
                }
            }

            it("does not cancel claimed rows") {
                val planUuid = TestDB.database.createOppfolgingsplan(
                    evalueringPaaminnelse = true,
                    evalueringsdato = LocalDate.of(2020, 1, 15),
                )
                val claimedType = OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_DINE_SYKMELDTE
                val claimedMessage = TestDB.database.exposedTransaction {
                    claimOutboxMessages(
                        messageType = claimedType,
                        now = now,
                        limit = 1,
                        leaseDuration = 5.minutes,
                    ).single()
                }
                claimedMessage.status shouldBe OutboxStatus.CLAIMED

                TestDB.database.createOppfolgingsplan(
                    evalueringPaaminnelse = false,
                    evalueringsdato = LocalDate.of(2026, 10, 15),
                )

                TestDB.database.findEvalueringMessage(claimedType, planUuid).status shouldBe OutboxStatus.CLAIMED
                TestDB.database.findEvalueringMessage(
                    OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_MIN_SIDE_ARBEIDSGIVER,
                    planUuid,
                ).status shouldBe OutboxStatus.CANCELLED
            }

            it("never cancels reminders when only drafts are persisted") {
                val planUuid = TestDB.database.createOppfolgingsplan(
                    evalueringPaaminnelse = true,
                    evalueringsdato = LocalDate.of(2026, 11, 15),
                )
                val draft = defaultPersistedOppfolgingsplanUtkast().copy(
                    sykmeldtFnr = defaultSykmeldt().fnr,
                    organisasjonsnummer = defaultSykmeldt().orgnummer,
                )
                TestDB.database.persistOppfolgingsplanUtkast(draft)

                OppfolgingsplanOutboxMessageType.evalueringPaaminnelseTypes.forEach { messageType ->
                    TestDB.database.findEvalueringMessage(messageType, planUuid).status shouldBe OutboxStatus.READY
                }
            }

            it("rolls back cancellation and draft deletion when enqueue fails") {
                val sykmeldt = defaultSykmeldt().copy(narmestelederId = "leader-rollback")
                val existingPlanUuid = TestDB.database.createOppfolgingsplan(
                    sykmeldt = sykmeldt,
                    evalueringPaaminnelse = true,
                    evalueringsdato = LocalDate.of(2026, 12, 15),
                )
                val draft = defaultPersistedOppfolgingsplanUtkast().copy(
                    narmesteLederId = sykmeldt.narmestelederId,
                    sykmeldtFnr = sykmeldt.fnr,
                    organisasjonsnummer = sykmeldt.orgnummer,
                )
                TestDB.database.persistOppfolgingsplanUtkast(draft)
                TestDB.database.rejectCreatedOutboxInserts()

                try {
                    shouldThrow<Exception> {
                        TestDB.database.createOppfolgingsplan(
                            sykmeldt = sykmeldt,
                            evalueringPaaminnelse = true,
                            evalueringsdato = LocalDate.of(2027, 1, 15),
                        )
                    }
                } finally {
                    TestDB.database.allowCreatedOutboxInserts()
                }

                TestDB.database.findAllOppfolgingsplanerBy(sykmeldt.fnr, sykmeldt.orgnummer).shouldHaveSize(1)
                TestDB.database.findOppfolgingsplanUtkastByNarmesteLederId(sykmeldt.narmestelederId).shouldNotBeNull()
                OppfolgingsplanOutboxMessageType.evalueringPaaminnelseTypes.forEach { messageType ->
                    TestDB.database.findEvalueringMessage(messageType, existingPlanUuid).status shouldBe OutboxStatus.READY
                }
            }

            it("retries a failed replacement transaction without duplicating reminder rows") {
                val sykmeldt = defaultSykmeldt().copy(narmestelederId = "leader-retry")
                val existingPlanUuid = TestDB.database.createOppfolgingsplan(
                    sykmeldt = sykmeldt,
                    evalueringPaaminnelse = true,
                    evalueringsdato = LocalDate.of(2027, 1, 15),
                )
                val replacementEvalueringsdato = LocalDate.of(2027, 2, 15)
                val draft = defaultPersistedOppfolgingsplanUtkast().copy(
                    narmesteLederId = sykmeldt.narmestelederId,
                    sykmeldtFnr = sykmeldt.fnr,
                    organisasjonsnummer = sykmeldt.orgnummer,
                )
                TestDB.database.persistOppfolgingsplanUtkast(draft)
                TestDB.database.rejectCreatedOutboxInserts()

                try {
                    shouldThrow<Exception> {
                        TestDB.database.createOppfolgingsplan(
                            sykmeldt = sykmeldt,
                            evalueringPaaminnelse = true,
                            evalueringsdato = replacementEvalueringsdato,
                        )
                    }
                } finally {
                    TestDB.database.allowCreatedOutboxInserts()
                }

                TestDB.database.findAllOppfolgingsplanerBy(sykmeldt.fnr, sykmeldt.orgnummer).shouldHaveSize(1)
                OppfolgingsplanOutboxMessageType.evalueringPaaminnelseTypes.forEach { messageType ->
                    TestDB.database.findEvalueringMessage(messageType, existingPlanUuid).status shouldBe OutboxStatus.READY
                }

                val replacementPlanUuid = TestDB.database.createOppfolgingsplan(
                    sykmeldt = sykmeldt,
                    evalueringPaaminnelse = true,
                    evalueringsdato = replacementEvalueringsdato,
                )

                val plans = TestDB.database.findAllOppfolgingsplanerBy(sykmeldt.fnr, sykmeldt.orgnummer)
                plans.shouldHaveSize(2)
                plans.count { it.uuid == replacementPlanUuid } shouldBe 1
                TestDB.database.countEvalueringPaaminnelseRows(replacementPlanUuid) shouldBe 2
                OppfolgingsplanOutboxMessageType.evalueringPaaminnelseTypes.forEach { messageType ->
                    TestDB.database.findEvalueringMessage(messageType, replacementPlanUuid).status shouldBe OutboxStatus.READY
                    TestDB.database.findEvalueringMessage(messageType, existingPlanUuid).apply {
                        status shouldBe OutboxStatus.CANCELLED
                        cancellationReason shouldBe OutboxCancellationReason.SUPERSEDED
                    }
                }
            }

            it("serializes two concurrent first plans so only the last plan retains READY reminders") {
                val sykmeldt = defaultSykmeldt().copy(narmestelederId = "leader-concurrent-first")

                TestDB.database.connection.use { barrierConnection ->
                    barrierConnection.prepareStatement(
                        "SELECT pg_advisory_lock(hashtextextended(? || ':' || ?, 0))",
                    ).use {
                        it.setString(1, sykmeldt.fnr)
                        it.setString(2, sykmeldt.orgnummer)
                        it.execute()
                    }

                    val plans = coroutineScope {
                        val first = async(Dispatchers.IO) {
                            TestDB.database.createOppfolgingsplan(
                                sykmeldt = sykmeldt,
                                evalueringPaaminnelse = true,
                                evalueringsdato = LocalDate.of(2027, 6, 15),
                            )
                        }
                        TestDB.database.awaitDatabaseCondition { countWaitingAdvisoryLocks() == 1 }

                        val second = async(Dispatchers.IO) {
                            TestDB.database.createOppfolgingsplan(
                                sykmeldt = sykmeldt.copy(narmestelederId = "leader-concurrent-second"),
                                evalueringPaaminnelse = true,
                                evalueringsdato = LocalDate.of(2027, 7, 15),
                            )
                        }
                        TestDB.database.awaitDatabaseCondition { countWaitingAdvisoryLocks() == 2 }

                        barrierConnection.prepareStatement(
                            "SELECT pg_advisory_unlock(hashtextextended(? || ':' || ?, 0))",
                        ).use {
                            it.setString(1, sykmeldt.fnr)
                            it.setString(2, sykmeldt.orgnummer)
                            it.execute()
                        }
                        listOf(first.await(), second.await())
                    }

                    TestDB.database.findAllOppfolgingsplanerBy(sykmeldt.fnr, sykmeldt.orgnummer)
                        .shouldHaveSize(2)
                    OppfolgingsplanOutboxMessageType.evalueringPaaminnelseTypes.forEach { messageType ->
                        TestDB.database.findEvalueringMessage(messageType, plans[0]).status shouldBe
                            OutboxStatus.CANCELLED
                        TestDB.database.findEvalueringMessage(messageType, plans[1]).status shouldBe
                            OutboxStatus.READY
                    }
                }
            }
        }

        describe("business metrics") {
            it("updates created and superseded counters by channel after successful commit") {
                val sykmeldt = defaultSykmeldt().copy(narmestelederId = "leader-metrics")
                val enqueuedBefore = outboxEnqueuedCount(OppfolgingsplanOutboxMessageType.CREATED)
                val createdBefore = OppfolgingsplanOutboxMessageType.evalueringPaaminnelseTypes.associateWith {
                    outboxMetricCount(it, outcome = "created")
                }
                val supersededBefore = OppfolgingsplanOutboxMessageType.evalueringPaaminnelseTypes.associateWith {
                    outboxMetricCount(it, outcome = "superseded")
                }

                TestDB.database.createOppfolgingsplan(
                    sykmeldt = sykmeldt,
                    evalueringPaaminnelse = true,
                    evalueringsdato = LocalDate.of(2027, 2, 15),
                )
                TestDB.database.createOppfolgingsplan(
                    sykmeldt = sykmeldt.copy(narmestelederId = "leader-metrics-replacement"),
                    evalueringPaaminnelse = false,
                    evalueringsdato = LocalDate.of(2027, 3, 15),
                )

                OppfolgingsplanOutboxMessageType.evalueringPaaminnelseTypes.forEach { messageType ->
                    outboxMetricCount(messageType, outcome = "created") - createdBefore.getValue(messageType) shouldBe 1.0
                    outboxMetricCount(messageType, outcome = "superseded") - supersededBefore.getValue(messageType) shouldBe 1.0
                }
                outboxEnqueuedCount(OppfolgingsplanOutboxMessageType.CREATED) - enqueuedBefore shouldBe 2.0
            }

            it("does not update counters when transaction rolls back") {
                val sykmeldt = defaultSykmeldt().copy(narmestelederId = "leader-metrics-rollback")
                val enqueuedBefore = outboxEnqueuedCount(OppfolgingsplanOutboxMessageType.CREATED)
                val createdBefore = OppfolgingsplanOutboxMessageType.evalueringPaaminnelseTypes.associateWith {
                    outboxMetricCount(it, outcome = "created")
                }
                val supersededBefore = OppfolgingsplanOutboxMessageType.evalueringPaaminnelseTypes.associateWith {
                    outboxMetricCount(it, outcome = "superseded")
                }
                TestDB.database.rejectCreatedOutboxInserts()

                try {
                    shouldThrow<Exception> {
                        TestDB.database.createOppfolgingsplan(
                            sykmeldt = sykmeldt,
                            evalueringPaaminnelse = true,
                            evalueringsdato = LocalDate.of(2027, 4, 15),
                        )
                    }
                } finally {
                    TestDB.database.allowCreatedOutboxInserts()
                }

                OppfolgingsplanOutboxMessageType.evalueringPaaminnelseTypes.forEach { messageType ->
                    outboxMetricCount(messageType, outcome = "created") shouldBe createdBefore.getValue(messageType)
                    outboxMetricCount(messageType, outcome = "superseded") shouldBe supersededBefore.getValue(messageType)
                }
                outboxEnqueuedCount(OppfolgingsplanOutboxMessageType.CREATED) shouldBe enqueuedBefore
            }
        }
    })

private fun sykmeldt(
    fnr: String,
    orgnummer: String,
    narmesteLederId: String,
    organisasjonsnavn: String,
): Sykmeldt = defaultSykmeldt().copy(
    fnr = fnr,
    orgnummer = orgnummer,
    narmestelederId = narmesteLederId,
    sykmeldinger = listOf(DineSykmeldteSykmelding(organisasjonsnavn)),
)

private suspend fun DatabaseInterface.createOppfolgingsplan(
    sykmeldt: Sykmeldt = defaultSykmeldt(),
    evalueringPaaminnelse: Boolean,
    evalueringsdato: LocalDate,
): UUID = OppfolgingsplanService(
    database = this,
    esyfovarselProducer = mockk<EsyfovarselProducer>(relaxed = true),
    pdlService = mockk<PdlService>(relaxed = true),
    aaregService = mockk<AaregService>(relaxed = true),
    unntaksvurderingService = mockk<UnntaksvurderingService>(relaxed = true),
    oppfolgingsplanFinalizationRepository = OppfolgingsplanFinalizationRepository(this),
    outboxLifecycleMetrics = MicrometerOutboxLifecycleMetrics(
        registry = METRICS_REGISTRY,
        observedMessageTypes = setOf(OppfolgingsplanOutboxMessageType.CREATED.value),
    ),
).createOppfolgingsplan(
    narmesteLederFnr = "10987654321",
    sykmeldt = sykmeldt,
    createOppfolgingsplanRequest = defaultOppfolgingsplan().copy(
        evalueringPaaminnelse = evalueringPaaminnelse,
        evalueringsdato = evalueringsdato,
    ),
)

private suspend fun DatabaseInterface.findEvalueringMessage(
    messageType: OppfolgingsplanOutboxMessageType,
    planUuid: UUID,
): OutboxMessage = findOutboxMessage(messageType, planUuid.toString()).shouldNotBeNull()

private fun outboxMetricCount(
    messageType: OppfolgingsplanOutboxMessageType,
    outcome: String,
): Double = METRICS_REGISTRY.counter(
    OPPFOLGINGSPLAN_EVALUERING_PAAMINNELSE_OUTBOX,
    "channel",
    requireNotNull(messageType.channelMetricLabel),
    "outcome",
    outcome,
).count()

private fun outboxEnqueuedCount(messageType: OppfolgingsplanOutboxMessageType): Double = METRICS_REGISTRY.counter(
    OUTBOX_ENQUEUED,
    "message_type",
    messageType.value,
).count()

private fun DatabaseInterface.rejectCreatedOutboxInserts() = connection.use { connection ->
    connection.createStatement().use { statement ->
        statement.execute(
            """
            ALTER TABLE outbox
            ADD CONSTRAINT reject_created_outbox_test_issue_430
            CHECK (message_type <> 'OPPFOLGINGSPLAN_CREATED')
            NOT VALID
            """.trimIndent(),
        )
    }
    connection.commit()
}

private fun DatabaseInterface.allowCreatedOutboxInserts() = connection.use { connection ->
    connection.createStatement().use { statement ->
        statement.execute(
            """
            ALTER TABLE outbox
            DROP CONSTRAINT reject_created_outbox_test_issue_430
            """.trimIndent(),
        )
    }
    connection.commit()
}

private fun DatabaseInterface.countEvalueringPaaminnelseRows(
    planUuid: UUID,
): Int = connection.use { connection ->
    connection.prepareStatement(
        """
        SELECT COUNT(*) AS row_count
        FROM outbox
        WHERE external_ref = ?
          AND message_type IN (?, ?)
        """.trimIndent(),
    ).use { statement ->
        var index = 0
        statement.setString(++index, planUuid.toString())
        statement.setString(
            ++index,
            OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_MIN_SIDE_ARBEIDSGIVER.value,
        )
        statement.setString(
            ++index,
            OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_DINE_SYKMELDTE.value,
        )
        statement.executeQuery().use { resultSet ->
            resultSet.next()
            resultSet.getInt("row_count")
        }
    }
}

private suspend fun DatabaseInterface.awaitDatabaseCondition(condition: DatabaseInterface.() -> Boolean) {
    withTimeout(10_000) {
        while (!condition()) {
            delay(10)
        }
    }
}

private fun DatabaseInterface.countWaitingAdvisoryLocks(): Int = connection.use { connection ->
    connection.prepareStatement(
        """
        SELECT COUNT(*)
        FROM pg_locks
        WHERE locktype = 'advisory'
          AND NOT granted
        """.trimIndent(),
    ).use { statement ->
        statement.executeQuery().use { resultSet ->
            resultSet.next()
            resultSet.getInt(1)
        }
    }
}
