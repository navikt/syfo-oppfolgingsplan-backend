package no.nav.syfo.oppfolgingsplan.outbox

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.TestDB
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.outbox.OutboxWorker
import no.nav.syfo.application.outbox.db.findOutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxCancellationReason
import no.nav.syfo.application.outbox.domain.OutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxStatus
import no.nav.syfo.defaultPersistedOppfolgingsplan
import no.nav.syfo.defaultSykmeldt
import no.nav.syfo.narmesteleder.client.INarmestelederClient
import no.nav.syfo.narmesteleder.client.Narmesteleder
import no.nav.syfo.oppfolgingsplan.db.findOpprettOppfolgingsplanPaaminnelseBy
import no.nav.syfo.oppfolgingsplan.service.OPPRETT_OPPFOLGINGSPLAN_PAAMINNELSE_ETTER_DAGER
import no.nav.syfo.oppfolgingsplan.service.OpprettOppfolgingsplanPaaminnelseService
import no.nav.syfo.persistOppfolgingsplan
import no.nav.syfo.sykmelding.db.SykmeldingsperiodeRepository
import no.nav.syfo.sykmelding.db.domain.SykmeldingsperiodeToStore
import no.nav.syfo.util.configuredJacksonMapper
import no.nav.syfo.varsel.budstikka.infrastructure.BudstikkaPublisher
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class OpprettOppfolgingsplanPaaminnelseOutboxIntegrationTest :
    DescribeSpec({
        val zone = ZoneId.of("Europe/Oslo")
        val orderedAt = Instant.parse("2025-06-19T10:00:00Z")
        val sykmeldingsperiodeFom = LocalDate.of(2025, 6, 1)
        val availableAt = sykmeldingsperiodeFom
            .plusDays(OPPRETT_OPPFOLGINGSPLAN_PAAMINNELSE_ETTER_DAGER)
            .atStartOfDay(zone)
            .toInstant()
        val repository = SykmeldingsperiodeRepository(TestDB.database)
        val service = OpprettOppfolgingsplanPaaminnelseService(
            database = TestDB.database,
            sykmeldingsperiodeRepository = repository,
            clock = Clock.fixed(orderedAt, zone),
        )

        beforeTest {
            TestDB.clearAllData()
            repository.storeSykmeldingsperioder(
                listOf(
                    SykmeldingsperiodeToStore(
                        sykmeldtFnr = defaultSykmeldt().fnr,
                        organisasjonsnummer = defaultSykmeldt().orgnummer,
                        sykmeldingId = "sykmelding",
                        fom = sykmeldingsperiodeFom,
                        tom = sykmeldingsperiodeFom.plusDays(30),
                    ),
                ),
            )
        }

        fun activeNarmestelederClient(): INarmestelederClient = mockk {
            coEvery { findActiveNarmesteleder(any(), any()) } returns Narmesteleder(
                id = UUID.fromString("9e629cad-a60c-464c-98ef-f30dd33f2da6"),
                nationalIdentificationNumber = "10987654321",
                emailAddresses = emptyList(),
            )
        }

        fun arbeidsgiverHandler(
            publisher: BudstikkaPublisher,
            narmestelederClient: INarmestelederClient = activeNarmestelederClient(),
        ) = OpprettOppfolgingsplanPaaminnelseArbeidsgiverOutboxHandler(
            database = TestDB.database,
            opprettOppfolgingsplanPaaminnelseService = service,
            publisher = publisher,
            narmestelederClient = narmestelederClient,
        )

        it("creates a scheduled outbox command atomically when a reminder is ordered") {
            service.activateOpprettOppfolgingsplanPaaminnelse(defaultSykmeldt())

            val opprettOppfolgingsplanPaaminnelse =
                TestDB.database.findOpprettOppfolgingsplanPaaminnelseBy(
                    defaultSykmeldt().fnr,
                    defaultSykmeldt().orgnummer,
                ).shouldNotBeNull()
            val message = TestDB.database.findOpprettOppfolgingsplanPaaminnelseOutboxMessage(
                OppfolgingsplanOutboxMessageType.PAAMINNELSE_ARBEIDSGIVER,
                opprettOppfolgingsplanPaaminnelse.uuid,
            ).shouldNotBeNull()
            val dineSykmeldteMessage = TestDB.database.findOpprettOppfolgingsplanPaaminnelseOutboxMessage(
                OppfolgingsplanOutboxMessageType.PAAMINNELSE_DINE_SYKMELDTE,
                opprettOppfolgingsplanPaaminnelse.uuid,
            ).shouldNotBeNull()

            message.externalRef shouldBe opprettOppfolgingsplanPaaminnelse.uuid.toString()
            message.availableAt shouldBe availableAt
            message.status shouldBe OutboxStatus.READY
            message.payload.contains(defaultSykmeldt().fnr) shouldBe false
            message.payload.contains(defaultSykmeldt().orgnummer) shouldBe false
            dineSykmeldteMessage.externalRef shouldBe opprettOppfolgingsplanPaaminnelse.uuid.toString()
            dineSykmeldteMessage.availableAt shouldBe availableAt
            dineSykmeldteMessage.status shouldBe OutboxStatus.READY
            (message.uuid == dineSykmeldteMessage.uuid) shouldBe false
            message.payloadBestillingId() shouldBe opprettOppfolgingsplanPaaminnelse.bestillingId
            dineSykmeldteMessage.payloadBestillingId() shouldBe opprettOppfolgingsplanPaaminnelse.bestillingId
        }

        it("keeps the scheduled outbox command when a reminder is ordered again") {
            service.activateOpprettOppfolgingsplanPaaminnelse(defaultSykmeldt())
            val opprettOppfolgingsplanPaaminnelse =
                TestDB.database.findOpprettOppfolgingsplanPaaminnelseBy(
                    defaultSykmeldt().fnr,
                    defaultSykmeldt().orgnummer,
                ).shouldNotBeNull()
            val firstMessage = TestDB.database.findOpprettOppfolgingsplanPaaminnelseOutboxMessage(
                OppfolgingsplanOutboxMessageType.PAAMINNELSE_ARBEIDSGIVER,
                opprettOppfolgingsplanPaaminnelse.uuid,
            ).shouldNotBeNull()

            service.activateOpprettOppfolgingsplanPaaminnelse(defaultSykmeldt())

            TestDB.database.findOpprettOppfolgingsplanPaaminnelseOutboxMessage(
                OppfolgingsplanOutboxMessageType.PAAMINNELSE_ARBEIDSGIVER,
                opprettOppfolgingsplanPaaminnelse.uuid,
            ) shouldBe firstMessage
            TestDB.database.findOpprettOppfolgingsplanPaaminnelseOutboxMessage(
                OppfolgingsplanOutboxMessageType.PAAMINNELSE_DINE_SYKMELDTE,
                opprettOppfolgingsplanPaaminnelse.uuid,
            ).shouldNotBeNull()
            TestDB.database.findOpprettOppfolgingsplanPaaminnelseBy(
                defaultSykmeldt().fnr,
                defaultSykmeldt().orgnummer,
            ).shouldNotBeNull().bestillingId shouldBe opprettOppfolgingsplanPaaminnelse.bestillingId
        }

        it("cancels a reminder that has been deactivated before delivery") {
            service.activateOpprettOppfolgingsplanPaaminnelse(defaultSykmeldt())
            service.deactivateOpprettOppfolgingsplanPaaminnelse(defaultSykmeldt())
            val opprettOppfolgingsplanPaaminnelse =
                TestDB.database.findOpprettOppfolgingsplanPaaminnelseBy(
                    defaultSykmeldt().fnr,
                    defaultSykmeldt().orgnummer,
                ).shouldNotBeNull()
            val publisher = mockk<BudstikkaPublisher>(relaxed = true)
            val worker = OutboxWorker(
                database = TestDB.database,
                handlers = listOf(
                    arbeidsgiverHandler(publisher),
                ),
                clock = Clock.fixed(availableAt, zone),
            )

            TestDB.database.findOpprettOppfolgingsplanPaaminnelseOutboxMessage(
                OppfolgingsplanOutboxMessageType.PAAMINNELSE_ARBEIDSGIVER,
                opprettOppfolgingsplanPaaminnelse.uuid,
            ).shouldNotBeNull().cancellationReason shouldBe OutboxCancellationReason.NO_LONGER_REQUESTED
            worker.runOnce().sent shouldBe 0
            coVerify(exactly = 0) {
                publisher.publishOpprettOppfolgingsplanPaaminnelse(any(), any(), any(), any(), any())
            }
        }

        it("sends a new reminder when it is activated after a cancelled reminder") {
            service.activateOpprettOppfolgingsplanPaaminnelse(defaultSykmeldt())
            service.deactivateOpprettOppfolgingsplanPaaminnelse(defaultSykmeldt())
            val publisher = mockk<BudstikkaPublisher>(relaxed = true)
            val worker = OutboxWorker(
                database = TestDB.database,
                handlers = listOf(
                    arbeidsgiverHandler(publisher),
                    OpprettOppfolgingsplanPaaminnelseDineSykmeldteOutboxHandler(
                        TestDB.database,
                        service,
                        publisher,
                    ),
                ),
                clock = Clock.fixed(availableAt, zone),
            )

            val cancelledStates = TestDB.database.findOpprettOppfolgingsplanPaaminnelseOutboxStates(
                TestDB.database.findOpprettOppfolgingsplanPaaminnelseBy(
                    defaultSykmeldt().fnr,
                    defaultSykmeldt().orgnummer,
                ).shouldNotBeNull().uuid,
            )
            cancelledStates.count { it.first == OutboxStatus.CANCELLED } shouldBe 2

            service.activateOpprettOppfolgingsplanPaaminnelse(defaultSykmeldt())

            worker.runOnce().sent shouldBe 2
            coVerify(exactly = 1) {
                publisher.publishOpprettOppfolgingsplanPaaminnelse(any(), any(), any(), any(), any())
            }
            coVerify(exactly = 1) {
                publisher.publishOpprettOppfolgingsplanPaaminnelseToDineSykmeldte(any(), any(), any(), any())
            }
        }

        it("cancels queued reminders before rapid reactivation sends one new reminder per channel") {
            service.activateOpprettOppfolgingsplanPaaminnelse(defaultSykmeldt())
            val opprettOppfolgingsplanPaaminnelse =
                TestDB.database.findOpprettOppfolgingsplanPaaminnelseBy(
                    defaultSykmeldt().fnr,
                    defaultSykmeldt().orgnummer,
                ).shouldNotBeNull()
            service.deactivateOpprettOppfolgingsplanPaaminnelse(defaultSykmeldt())
            service.activateOpprettOppfolgingsplanPaaminnelse(defaultSykmeldt())
            val currentBestillingId = TestDB.database.findOpprettOppfolgingsplanPaaminnelseBy(
                defaultSykmeldt().fnr,
                defaultSykmeldt().orgnummer,
            ).shouldNotBeNull().bestillingId
            (currentBestillingId == opprettOppfolgingsplanPaaminnelse.bestillingId) shouldBe false
            val publisher = mockk<BudstikkaPublisher>(relaxed = true)
            val worker = OutboxWorker(
                database = TestDB.database,
                handlers = listOf(
                    arbeidsgiverHandler(publisher),
                    OpprettOppfolgingsplanPaaminnelseDineSykmeldteOutboxHandler(
                        TestDB.database,
                        service,
                        publisher,
                    ),
                ),
                clock = Clock.fixed(availableAt, zone),
            )

            worker.runOnce().sent shouldBe 2

            val states =
                TestDB.database.findOpprettOppfolgingsplanPaaminnelseOutboxStates(
                    opprettOppfolgingsplanPaaminnelse.uuid,
                )
            states.count { it.first == OutboxStatus.CANCELLED } shouldBe 2
            states
                .filter { it.first == OutboxStatus.CANCELLED }
                .all { it.second == OutboxCancellationReason.NO_LONGER_REQUESTED } shouldBe true
            states.count { it.first == OutboxStatus.SENT } shouldBe 2
            coVerify(exactly = 1) {
                publisher.publishOpprettOppfolgingsplanPaaminnelse(any(), any(), any(), any(), any())
            }
            coVerify(exactly = 1) {
                publisher.publishOpprettOppfolgingsplanPaaminnelseToDineSykmeldte(any(), any(), any(), any())
            }
        }

        it("cancels claimed messages from an old generation and sends only the current generation") {
            service.activateOpprettOppfolgingsplanPaaminnelse(defaultSykmeldt())
            val source = TestDB.database.findOpprettOppfolgingsplanPaaminnelseBy(
                defaultSykmeldt().fnr,
                defaultSykmeldt().orgnummer,
            ).shouldNotBeNull()
            TestDB.database.claimOpprettOppfolgingsplanPaaminnelseMessages(source.uuid, availableAt.plusSeconds(60))

            service.deactivateOpprettOppfolgingsplanPaaminnelse(defaultSykmeldt())
            service.activateOpprettOppfolgingsplanPaaminnelse(defaultSykmeldt())
            val currentBestillingId = TestDB.database.findOpprettOppfolgingsplanPaaminnelseBy(
                defaultSykmeldt().fnr,
                defaultSykmeldt().orgnummer,
            ).shouldNotBeNull().bestillingId
            TestDB.database.expireOpprettOppfolgingsplanPaaminnelseClaims(source.uuid, availableAt.minusSeconds(1))

            val publishedBestillingIds = mutableListOf<UUID>()
            val publishedEventIds = mutableListOf<UUID>()
            val publisher = mockk<BudstikkaPublisher>(relaxed = true)
            coEvery {
                publisher.publishOpprettOppfolgingsplanPaaminnelse(any(), any(), any(), any(), any())
            } answers {
                publishedBestillingIds.add(firstArg())
                publishedEventIds.add(arg(3))
            }
            coEvery {
                publisher.publishOpprettOppfolgingsplanPaaminnelseToDineSykmeldte(any(), any(), any(), any())
            } answers {
                publishedBestillingIds.add(firstArg())
                publishedEventIds.add(arg(3))
            }
            val worker = OutboxWorker(
                database = TestDB.database,
                handlers = listOf(
                    arbeidsgiverHandler(publisher),
                    OpprettOppfolgingsplanPaaminnelseDineSykmeldteOutboxHandler(
                        TestDB.database,
                        service,
                        publisher,
                    ),
                ),
                clock = Clock.fixed(availableAt, zone),
            )

            val result = worker.runOnce()

            result.sent shouldBe 2
            result.cancelled shouldBe 2
            publishedBestillingIds shouldBe listOf(currentBestillingId, currentBestillingId)
            publishedEventIds.toSet().size shouldBe 2
            val states = TestDB.database.findOpprettOppfolgingsplanPaaminnelseOutboxStates(source.uuid)
            states.count {
                it.first == OutboxStatus.CANCELLED && it.second == OutboxCancellationReason.SUPERSEDED
            } shouldBe 2
        }

        it("cancels only the employer message when no active narmesteleder exists") {
            service.activateOpprettOppfolgingsplanPaaminnelse(defaultSykmeldt())
            val source = TestDB.database.findOpprettOppfolgingsplanPaaminnelseBy(
                defaultSykmeldt().fnr,
                defaultSykmeldt().orgnummer,
            ).shouldNotBeNull()
            val narmestelederClient = mockk<INarmestelederClient> {
                coEvery { findActiveNarmesteleder(any(), any()) } returns null
            }
            val publisher = mockk<BudstikkaPublisher>(relaxed = true)
            val worker = OutboxWorker(
                database = TestDB.database,
                handlers = listOf(
                    arbeidsgiverHandler(publisher, narmestelederClient),
                    OpprettOppfolgingsplanPaaminnelseDineSykmeldteOutboxHandler(
                        TestDB.database,
                        service,
                        publisher,
                    ),
                ),
                clock = Clock.fixed(availableAt, zone),
            )

            val result = worker.runOnce()

            result.cancelled shouldBe 1
            result.sent shouldBe 1
            val states = TestDB.database.findOpprettOppfolgingsplanPaaminnelseOutboxStates(source.uuid)
            states.count {
                it.first == OutboxStatus.CANCELLED &&
                    it.second == OutboxCancellationReason.SOURCE_NO_LONGER_ELIGIBLE
            } shouldBe 1
            states.count { it.first == OutboxStatus.SENT } shouldBe 1
            coVerify(exactly = 0) {
                publisher.publishOpprettOppfolgingsplanPaaminnelse(any(), any(), any(), any(), any())
            }
            coVerify(exactly = 1) {
                publisher.publishOpprettOppfolgingsplanPaaminnelseToDineSykmeldte(any(), any(), any(), any())
            }
        }

        it("delivers a rolling-deployment payload without bestillingId") {
            service.activateOpprettOppfolgingsplanPaaminnelse(defaultSykmeldt())
            val source = TestDB.database.findOpprettOppfolgingsplanPaaminnelseBy(
                defaultSykmeldt().fnr,
                defaultSykmeldt().orgnummer,
            ).shouldNotBeNull()
            TestDB.database.removeBestillingIdFromPayload(
                source.uuid,
                OppfolgingsplanOutboxMessageType.PAAMINNELSE_DINE_SYKMELDTE,
            )
            val publisher = mockk<BudstikkaPublisher>(relaxed = true)
            val worker = OutboxWorker(
                database = TestDB.database,
                handlers = listOf(
                    OpprettOppfolgingsplanPaaminnelseDineSykmeldteOutboxHandler(
                        TestDB.database,
                        service,
                        publisher,
                    ),
                ),
                clock = Clock.fixed(availableAt, zone),
            )

            worker.runOnce().sent shouldBe 1

            coVerify(exactly = 1) {
                publisher.publishOpprettOppfolgingsplanPaaminnelseToDineSykmeldte(
                    bestillingId = source.bestillingId,
                    sykmeldtFnr = any(),
                    orgnummer = any(),
                    eventId = any(),
                )
            }
        }

        it("cancels a reminder when its source has been deleted before delivery") {
            service.activateOpprettOppfolgingsplanPaaminnelse(defaultSykmeldt())
            val opprettOppfolgingsplanPaaminnelse =
                TestDB.database.findOpprettOppfolgingsplanPaaminnelseBy(
                    defaultSykmeldt().fnr,
                    defaultSykmeldt().orgnummer,
                ).shouldNotBeNull()
            TestDB.database.deleteOpprettOppfolgingsplanPaaminnelse(
                opprettOppfolgingsplanPaaminnelse.uuid,
            ) shouldBe 1
            val publisher = mockk<BudstikkaPublisher>(relaxed = true)
            val worker = OutboxWorker(
                database = TestDB.database,
                handlers = listOf(
                    arbeidsgiverHandler(publisher),
                ),
                clock = Clock.fixed(availableAt, zone),
            )

            worker.runOnce().cancelled shouldBe 1

            TestDB.database.findOpprettOppfolgingsplanPaaminnelseOutboxMessage(
                OppfolgingsplanOutboxMessageType.PAAMINNELSE_ARBEIDSGIVER,
                opprettOppfolgingsplanPaaminnelse.uuid,
            ).shouldNotBeNull().cancellationReason shouldBe OutboxCancellationReason.SOURCE_NOT_FOUND
            coVerify(exactly = 0) {
                publisher.publishOpprettOppfolgingsplanPaaminnelse(any(), any(), any(), any(), any())
            }
        }

        it("cancels a reminder when its source period has been superseded before delivery") {
            service.activateOpprettOppfolgingsplanPaaminnelse(defaultSykmeldt())
            val opprettOppfolgingsplanPaaminnelse =
                TestDB.database.findOpprettOppfolgingsplanPaaminnelseBy(
                    defaultSykmeldt().fnr,
                    defaultSykmeldt().orgnummer,
                ).shouldNotBeNull()
            repository.invalidateSykmelding("sykmelding")
            repository.storeSykmeldingsperioder(
                listOf(
                    SykmeldingsperiodeToStore(
                        sykmeldtFnr = defaultSykmeldt().fnr,
                        organisasjonsnummer = defaultSykmeldt().orgnummer,
                        sykmeldingId = "changed-sykmelding",
                        fom = sykmeldingsperiodeFom,
                        tom = sykmeldingsperiodeFom.plusDays(30),
                    ),
                ),
            )
            val publisher = mockk<BudstikkaPublisher>(relaxed = true)
            val worker = OutboxWorker(
                database = TestDB.database,
                handlers = listOf(
                    arbeidsgiverHandler(publisher),
                ),
                clock = Clock.fixed(availableAt, zone),
            )

            worker.runOnce().cancelled shouldBe 1

            TestDB.database.findOpprettOppfolgingsplanPaaminnelseOutboxMessage(
                OppfolgingsplanOutboxMessageType.PAAMINNELSE_ARBEIDSGIVER,
                opprettOppfolgingsplanPaaminnelse.uuid,
            ).shouldNotBeNull().cancellationReason shouldBe OutboxCancellationReason.SUPERSEDED
            coVerify(exactly = 0) {
                publisher.publishOpprettOppfolgingsplanPaaminnelse(any(), any(), any(), any(), any())
            }
        }

        it("cancels a reminder when an oppfolgingsplan has been created before delivery") {
            service.activateOpprettOppfolgingsplanPaaminnelse(defaultSykmeldt())
            val opprettOppfolgingsplanPaaminnelse =
                TestDB.database.findOpprettOppfolgingsplanPaaminnelseBy(
                    defaultSykmeldt().fnr,
                    defaultSykmeldt().orgnummer,
                ).shouldNotBeNull()
            TestDB.database.persistOppfolgingsplan(
                defaultPersistedOppfolgingsplan().copy(createdAt = availableAt),
            )
            val publisher = mockk<BudstikkaPublisher>(relaxed = true)
            val worker = OutboxWorker(
                database = TestDB.database,
                handlers = listOf(
                    arbeidsgiverHandler(publisher),
                ),
                clock = Clock.fixed(availableAt, zone),
            )

            worker.runOnce().cancelled shouldBe 1

            TestDB.database.findOpprettOppfolgingsplanPaaminnelseOutboxMessage(
                OppfolgingsplanOutboxMessageType.PAAMINNELSE_ARBEIDSGIVER,
                opprettOppfolgingsplanPaaminnelse.uuid,
            ).shouldNotBeNull().cancellationReason shouldBe OutboxCancellationReason.SOURCE_NO_LONGER_ELIGIBLE
            coVerify(exactly = 0) {
                publisher.publishOpprettOppfolgingsplanPaaminnelse(any(), any(), any(), any(), any())
            }
        }

        it("cancels a reminder when its sykmeldingsperiode is no longer active") {
            service.activateOpprettOppfolgingsplanPaaminnelse(defaultSykmeldt())
            val opprettOppfolgingsplanPaaminnelse =
                TestDB.database.findOpprettOppfolgingsplanPaaminnelseBy(
                    defaultSykmeldt().fnr,
                    defaultSykmeldt().orgnummer,
                ).shouldNotBeNull()
            repository.invalidateSykmelding("sykmelding")
            val publisher = mockk<BudstikkaPublisher>(relaxed = true)
            val worker = OutboxWorker(
                database = TestDB.database,
                handlers = listOf(
                    arbeidsgiverHandler(publisher),
                ),
                clock = Clock.fixed(availableAt, zone),
            )

            worker.runOnce().cancelled shouldBe 1

            TestDB.database.findOpprettOppfolgingsplanPaaminnelseOutboxMessage(
                OppfolgingsplanOutboxMessageType.PAAMINNELSE_ARBEIDSGIVER,
                opprettOppfolgingsplanPaaminnelse.uuid,
            ).shouldNotBeNull().cancellationReason shouldBe OutboxCancellationReason.SOURCE_NO_LONGER_ELIGIBLE
            coVerify(exactly = 0) {
                publisher.publishOpprettOppfolgingsplanPaaminnelse(any(), any(), any(), any(), any())
            }
        }

        it("publishes an active reminder to Budstikka with the outbox event ID") {
            service.activateOpprettOppfolgingsplanPaaminnelse(defaultSykmeldt())
            val opprettOppfolgingsplanPaaminnelse =
                TestDB.database.findOpprettOppfolgingsplanPaaminnelseBy(
                    defaultSykmeldt().fnr,
                    defaultSykmeldt().orgnummer,
                ).shouldNotBeNull()
            val message = TestDB.database.findOpprettOppfolgingsplanPaaminnelseOutboxMessage(
                OppfolgingsplanOutboxMessageType.PAAMINNELSE_ARBEIDSGIVER,
                opprettOppfolgingsplanPaaminnelse.uuid,
            ).shouldNotBeNull()
            val publisher = mockk<BudstikkaPublisher>()
            coEvery {
                publisher.publishOpprettOppfolgingsplanPaaminnelse(any(), any(), any(), any(), any())
            } returns Unit
            val worker = OutboxWorker(
                database = TestDB.database,
                handlers = listOf(
                    arbeidsgiverHandler(publisher),
                ),
                clock = Clock.fixed(availableAt, zone),
            )

            worker.runOnce().sent shouldBe 1

            coVerify(exactly = 1) {
                publisher.publishOpprettOppfolgingsplanPaaminnelse(
                    bestillingId = opprettOppfolgingsplanPaaminnelse.bestillingId,
                    eventId = message.uuid,
                    sykmeldtFnr = defaultSykmeldt().fnr,
                    orgnummer = defaultSykmeldt().orgnummer,
                    narmestelederId = UUID.fromString("9e629cad-a60c-464c-98ef-f30dd33f2da6"),
                )
            }
        }

        it("publishes an active reminder to Dine Sykmeldte with its own outbox event ID") {
            service.activateOpprettOppfolgingsplanPaaminnelse(defaultSykmeldt())
            val opprettOppfolgingsplanPaaminnelse =
                TestDB.database.findOpprettOppfolgingsplanPaaminnelseBy(
                    defaultSykmeldt().fnr,
                    defaultSykmeldt().orgnummer,
                ).shouldNotBeNull()
            val message = TestDB.database.findOpprettOppfolgingsplanPaaminnelseOutboxMessage(
                OppfolgingsplanOutboxMessageType.PAAMINNELSE_DINE_SYKMELDTE,
                opprettOppfolgingsplanPaaminnelse.uuid,
            ).shouldNotBeNull()
            val publisher = mockk<BudstikkaPublisher>()
            coEvery {
                publisher.publishOpprettOppfolgingsplanPaaminnelseToDineSykmeldte(any(), any(), any(), any())
            } returns Unit
            val worker = OutboxWorker(
                database = TestDB.database,
                handlers = listOf(
                    OpprettOppfolgingsplanPaaminnelseDineSykmeldteOutboxHandler(
                        TestDB.database,
                        service,
                        publisher,
                    ),
                ),
                clock = Clock.fixed(availableAt, zone),
            )

            worker.runOnce().sent shouldBe 1

            coVerify(exactly = 1) {
                publisher.publishOpprettOppfolgingsplanPaaminnelseToDineSykmeldte(
                    bestillingId = opprettOppfolgingsplanPaaminnelse.bestillingId,
                    eventId = message.uuid,
                    sykmeldtFnr = defaultSykmeldt().fnr,
                    orgnummer = defaultSykmeldt().orgnummer,
                )
            }
        }
    })

private fun DatabaseInterface.deleteOpprettOppfolgingsplanPaaminnelse(
    opprettOppfolgingsplanPaaminnelseUuid: UUID,
): Int = connection.use { connection ->
    connection.prepareStatement("DELETE FROM paaminnelse WHERE uuid = ?").use { statement ->
        statement.setObject(1, opprettOppfolgingsplanPaaminnelseUuid)
        statement.executeUpdate()
    }.also {
        connection.commit()
    }
}

private suspend fun DatabaseInterface.findOpprettOppfolgingsplanPaaminnelseOutboxMessage(
    messageType: OppfolgingsplanOutboxMessageType,
    opprettOppfolgingsplanPaaminnelseUuid: UUID,
): OutboxMessage? {
    val dedupKey = connection.use { connection ->
        connection.prepareStatement(
            "SELECT dedup_key FROM outbox WHERE message_type = ? AND external_ref = ?",
        ).use { statement ->
            statement.setString(1, messageType.value)
            statement.setString(2, opprettOppfolgingsplanPaaminnelseUuid.toString())
            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) resultSet.getString("dedup_key") else null
            }
        }
    } ?: return null

    return findOutboxMessage(messageType, dedupKey)
}

private fun DatabaseInterface.findOpprettOppfolgingsplanPaaminnelseOutboxStates(
    opprettOppfolgingsplanPaaminnelseUuid: UUID,
): List<Pair<OutboxStatus, OutboxCancellationReason?>> = connection.use { connection ->
    connection.prepareStatement(
        "SELECT status, cancellation_reason FROM outbox WHERE external_ref = ?",
    ).use { statement ->
        statement.setString(1, opprettOppfolgingsplanPaaminnelseUuid.toString())
        statement.executeQuery().use { resultSet ->
            buildList {
                while (resultSet.next()) {
                    add(
                        OutboxStatus.valueOf(resultSet.getString("status")) to
                            resultSet
                                .getString("cancellation_reason")
                                ?.let(OutboxCancellationReason::fromDatabaseValue),
                    )
                }
            }
        }
    }
}

private fun OutboxMessage.payloadBestillingId(): UUID = UUID.fromString(
    configuredJacksonMapper.readTree(payload).get("bestillingId").asText(),
)

private fun DatabaseInterface.claimOpprettOppfolgingsplanPaaminnelseMessages(
    externalRef: UUID,
    leaseUntil: Instant,
) {
    connection.use { connection ->
        connection.prepareStatement(
            """
            UPDATE outbox
            SET status = 'CLAIMED',
                claim_token = gen_random_uuid(),
                lease_until = ?
            WHERE external_ref = ?
              AND status = 'READY'
            """.trimIndent(),
        ).use { statement ->
            statement.setTimestamp(1, Timestamp.from(leaseUntil))
            statement.setString(2, externalRef.toString())
            statement.executeUpdate() shouldBe 2
        }
        connection.commit()
    }
}

private fun DatabaseInterface.expireOpprettOppfolgingsplanPaaminnelseClaims(
    externalRef: UUID,
    leaseUntil: Instant,
) {
    connection.use { connection ->
        connection.prepareStatement(
            """
            UPDATE outbox
            SET lease_until = ?
            WHERE external_ref = ?
              AND status = 'CLAIMED'
            """.trimIndent(),
        ).use { statement ->
            statement.setTimestamp(1, Timestamp.from(leaseUntil))
            statement.setString(2, externalRef.toString())
            statement.executeUpdate() shouldBe 2
        }
        connection.commit()
    }
}

private fun DatabaseInterface.removeBestillingIdFromPayload(
    externalRef: UUID,
    messageType: OppfolgingsplanOutboxMessageType,
) {
    connection.use { connection ->
        connection.prepareStatement(
            """
            UPDATE outbox
            SET payload = payload - 'bestillingId'
            WHERE external_ref = ?
              AND message_type = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, externalRef.toString())
            statement.setString(2, messageType.value)
            statement.executeUpdate() shouldBe 1
        }
        connection.commit()
    }
}
