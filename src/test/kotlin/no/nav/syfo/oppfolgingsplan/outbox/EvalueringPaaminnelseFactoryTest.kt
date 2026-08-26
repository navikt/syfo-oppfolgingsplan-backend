package no.nav.syfo.oppfolgingsplan.outbox

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalDate

class EvalueringPaaminnelseFactoryTest :
    DescribeSpec({
        describe("create") {
            it("creates no definitions when reminders are disabled") {
                EvalueringPaaminnelseFactory.create(
                    enabled = false,
                    evalueringsdato = LocalDate.of(2026, 1, 15),
                ).shouldBeEmpty()
            }

            it("creates one definition per reminder channel") {
                EvalueringPaaminnelseFactory.create(
                    enabled = true,
                    evalueringsdato = LocalDate.of(2026, 1, 15),
                ).map { it.messageType } shouldBe
                    listOf(
                        OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_MIN_SIDE_ARBEIDSGIVER,
                        OppfolgingsplanOutboxMessageType.EVALUERING_PAAMINNELSE_DINE_SYKMELDTE,
                    )
            }

            it("schedules three calendar days before at 09:00 Europe Oslo in winter") {
                val definitions = EvalueringPaaminnelseFactory.create(
                    enabled = true,
                    evalueringsdato = LocalDate.of(2026, 1, 15),
                )

                definitions.map { it.availableAt }.distinct() shouldBe
                    listOf(Instant.parse("2026-01-12T08:00:00Z"))
            }

            it("uses the Europe Oslo offset after the daylight-saving transition") {
                val definitions = EvalueringPaaminnelseFactory.create(
                    enabled = true,
                    evalueringsdato = LocalDate.of(2026, 4, 1),
                )

                definitions.map { it.availableAt }.distinct() shouldBe
                    listOf(Instant.parse("2026-03-29T07:00:00Z"))
            }
        }
    })
