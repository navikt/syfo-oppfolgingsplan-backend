package no.nav.syfo.oppfolgingsplan.outbox

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import no.nav.syfo.TestDB
import no.nav.syfo.application.outbox.OutboxProcessor
import no.nav.syfo.application.outbox.db.findOutboxFor
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import no.nav.syfo.application.outbox.domain.OutboxStatus
import no.nav.syfo.application.outbox.execute
import no.nav.syfo.defaultSykmeldt
import no.nav.syfo.oppfolgingsplan.db.findPaaminnelseBy
import no.nav.syfo.oppfolgingsplan.db.paaminnelseDedupKey
import no.nav.syfo.oppfolgingsplan.service.PaaminnelseService
import no.nav.syfo.sykmelding.db.SykmeldingsperiodeRepository
import no.nav.syfo.sykmelding.db.domain.SykmeldingsperiodeToStore
import no.nav.syfo.util.configuredJacksonMapper
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PaaminnelseOutboxHandlerTest :
    DescribeSpec({
        val database = TestDB.database
        val zoneId = ZoneId.of("Europe/Oslo")
        val now = Instant.parse("2025-06-04T10:00:00Z")
        val forlopFom = LocalDate.of(2025, 6, 1)
        val repository = SykmeldingsperiodeRepository(database)
        fun service() = PaaminnelseService(database, repository, Clock.fixed(now, zoneId))
        fun processor() = OutboxProcessor(
            database,
            listOf(PaaminnelseOutboxHandler(Clock.fixed(now, zoneId))),
            Clock.fixed(now, zoneId),
        )
        fun seed() = repository.storeSykmeldingsperioder(
            listOf(
                SykmeldingsperiodeToStore(
                    sykmeldtFnr = defaultSykmeldt().fnr,
                    organisasjonsnummer = defaultSykmeldt().orgnummer,
                    sykmeldingId = "sykmelding",
                    fom = forlopFom,
                    tom = forlopFom.plusDays(60),
                ),
            ),
        )
        fun outbox() = database.execute { connection ->
            val paaminnelse = connection.findPaaminnelseBy(defaultSykmeldt().fnr, defaultSykmeldt().orgnummer)
            connection.findOutboxFor(
                OutboxMessageType.PAAMINNELSE_OPPFOLGINGSPLAN,
                paaminnelseDedupKey(requireNotNull(paaminnelse).uuid, forlopFom),
            )
        }

        beforeTest {
            TestDB.clearAllData()
        }

        it("enqueues a non-PII payload when a reminder is ordered") {
            seed()
            service().activatePaaminnelse(defaultSykmeldt())

            val message = outbox().shouldNotBeNull()
            message.status shouldBe OutboxStatus.KLAR
            message.scheduledAt shouldBe now
            configuredJacksonMapper.readTree(message.payload).path("messageType").asText() shouldBe
                OutboxMessageType.PAAMINNELSE_OPPFOLGINGSPLAN.name
            message.payload shouldNotContain defaultSykmeldt().fnr
            message.payload shouldNotContain defaultSykmeldt().orgnummer
        }

        it("sends an ordered reminder") {
            seed()
            service().activatePaaminnelse(defaultSykmeldt())

            processor().processReadyMessages(
                OutboxMessageType.PAAMINNELSE_OPPFOLGINGSPLAN,
            )

            outbox().shouldNotBeNull().status shouldBe OutboxStatus.SENDT
        }

        it("marks a deactivated reminder as not relevant") {
            seed()
            service().activatePaaminnelse(defaultSykmeldt())
            service().deactivatePaaminnelse(defaultSykmeldt())

            processor().processReadyMessages(
                OutboxMessageType.PAAMINNELSE_OPPFOLGINGSPLAN,
            )

            outbox().shouldNotBeNull().status shouldBe OutboxStatus.IKKE_RELEVANT
        }
    })
