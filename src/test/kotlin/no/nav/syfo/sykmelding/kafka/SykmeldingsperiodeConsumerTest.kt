package no.nav.syfo.sykmelding.kafka

import com.fasterxml.jackson.core.JsonProcessingException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.application.kafka.KafkaEnv
import no.nav.syfo.sykmelding.db.SykmeldingsperiodeRepository
import no.nav.syfo.sykmelding.db.domain.SykmeldingsperiodeToStore
import no.nav.syfo.sykmelding.kafka.model.Arbeidsgiver
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverSykmelding
import no.nav.syfo.sykmelding.kafka.model.Event
import no.nav.syfo.sykmelding.kafka.model.KafkaMetadata
import no.nav.syfo.sykmelding.kafka.model.SendtSykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.model.SykmeldingsperiodeAGDTO
import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SykmeldingsperiodeConsumerTest :
    DescribeSpec({
        val repository = mockk<SykmeldingsperiodeRepository>()
        val fixedClock = Clock.fixed(Instant.parse("2025-06-01T00:00:00Z"), ZoneId.of("Europe/Oslo"))
        val consumer = SykmeldingsperiodeConsumer(
            sykmeldingsperiodeRepository = repository,
            kafkaEnv = KafkaEnv.createForLocal(),
            clock = fixedClock,
        )

        beforeTest {
            clearAllMocks()
        }

        describe("processRecord") {
            it("stores all recent sykmeldingsperioder from a Kafka message") {
                every { repository.storeSykmeldingsperioder(any()) } returns 2

                consumer.processRecord(
                    ConsumerRecord(
                        SYKMELDINGSPERIODE_TOPIC,
                        0,
                        0L,
                        "sykmelding-1",
                        kafkaMessage(
                            perioder = listOf(
                                SykmeldingsperiodeAGDTO(
                                    fom = LocalDate.of(2025, 1, 1),
                                    tom = LocalDate.of(2025, 1, 31),
                                ),
                                SykmeldingsperiodeAGDTO(
                                    fom = LocalDate.of(2025, 2, 1),
                                    tom = LocalDate.of(2025, 2, 28),
                                ),
                            ),
                        ),
                    ),
                )

                verify(exactly = 1) {
                    repository.storeSykmeldingsperioder(
                        withArg { sykmeldingsperioder ->
                            sykmeldingsperioder.shouldHaveSize(2)
                            sykmeldingsperioder[0] shouldBe SykmeldingsperiodeToStore(
                                sykmeldtFnr = "12345678901",
                                organisasjonsnummer = "987654321",
                                sykmeldingId = "sykmelding-1",
                                fom = LocalDate.of(2025, 1, 1),
                                tom = LocalDate.of(2025, 1, 31),
                            )
                            sykmeldingsperioder[1] shouldBe SykmeldingsperiodeToStore(
                                sykmeldtFnr = "12345678901",
                                organisasjonsnummer = "987654321",
                                sykmeldingId = "sykmelding-1",
                                fom = LocalDate.of(2025, 2, 1),
                                tom = LocalDate.of(2025, 2, 28),
                            )
                        },
                    )
                }
            }

            it("filters out periods older than two years") {
                every { repository.storeSykmeldingsperioder(any()) } returns 1

                consumer.processRecord(
                    ConsumerRecord(
                        SYKMELDINGSPERIODE_TOPIC,
                        0,
                        0L,
                        "sykmelding-2",
                        kafkaMessage(
                            perioder = listOf(
                                SykmeldingsperiodeAGDTO(
                                    fom = LocalDate.of(2023, 4, 1),
                                    tom = LocalDate.of(2023, 4, 30),
                                ),
                                SykmeldingsperiodeAGDTO(
                                    fom = LocalDate.of(2025, 4, 1),
                                    tom = LocalDate.of(2025, 4, 30),
                                ),
                            ),
                        ),
                    ),
                )

                verify(exactly = 1) {
                    repository.storeSykmeldingsperioder(
                        withArg { sykmeldingsperioder ->
                            sykmeldingsperioder.shouldHaveSize(1)
                            sykmeldingsperioder.single().tom shouldBe LocalDate.of(2025, 4, 30)
                        },
                    )
                }
            }

            it("invalidates matching rows for tombstones") {
                every { repository.invalidateSykmelding("sykmelding-3") } returns 2

                consumer.processRecord(
                    ConsumerRecord(
                        SYKMELDINGSPERIODE_TOPIC,
                        0,
                        0L,
                        "sykmelding-3",
                        null,
                    ),
                )

                verify(exactly = 1) {
                    repository.invalidateSykmelding("sykmelding-3")
                }
            }

            it("includes period with tom exactly at the 2-year boundary") {
                every { repository.storeSykmeldingsperioder(any()) } returns 1

                // Clock is 2025-06-01, cutoff is 2023-06-01, tom = 2023-06-01 should be INCLUDED
                consumer.processRecord(
                    ConsumerRecord(
                        SYKMELDINGSPERIODE_TOPIC,
                        0,
                        0L,
                        "sykmelding-boundary",
                        kafkaMessage(
                            perioder = listOf(
                                SykmeldingsperiodeAGDTO(
                                    fom = LocalDate.of(2023, 5, 1),
                                    tom = LocalDate.of(2023, 6, 1),
                                ),
                            ),
                        ),
                    ),
                )

                verify(exactly = 1) {
                    repository.storeSykmeldingsperioder(
                        withArg { sykmeldingsperioder ->
                            sykmeldingsperioder.shouldHaveSize(1)
                            sykmeldingsperioder.single().tom shouldBe LocalDate.of(2023, 6, 1)
                        },
                    )
                }
            }

            it("throws on invalid JSON so offsets are not committed") {
                shouldThrow<JsonProcessingException> {
                    consumer.processRecord(
                        ConsumerRecord(
                            SYKMELDINGSPERIODE_TOPIC,
                            0,
                            0L,
                            "sykmelding-4",
                            "{invalid-json}",
                        ),
                    )
                }

                verify(exactly = 0) { repository.storeSykmeldingsperioder(any()) }
                verify(exactly = 0) { repository.invalidateSykmelding(any()) }
            }

            it("deserializes realistic raw Kafka JSON with unknown fields like brukerSvar") {
                every { repository.storeSykmeldingsperioder(any()) } returns 1

                val rawJson = """
                    {
                      "sykmelding": {
                        "id": "sykmelding-raw",
                        "sykmeldingsperioder": [
                          {"fom": "2025-01-10", "tom": "2025-01-20", "type": "AKTIVITET_IKKE_MULIG", "gradert": null, "behandlingsdager": null}
                        ],
                        "mottattTidspunkt": "2025-01-10T08:00:00Z",
                        "behandletTidspunkt": "2025-01-10T09:00:00Z",
                        "arbeidsgiver": {"orgnummer": "111222333", "orgNavn": "Foo AS"},
                        "merknader": null
                      },
                      "kafkaMetadata": {
                        "sykmeldingId": "sykmelding-raw",
                        "timestamp": "2025-01-10T10:00:00Z",
                        "fnr": "99887766554",
                        "source": "syfosmregister"
                      },
                      "event": {
                        "sykmeldingId": "sykmelding-raw",
                        "timestamp": "2025-01-10T10:00:00Z",
                        "statusEvent": "SENDT",
                        "arbeidsgiver": {"orgnummer": "111222333", "juridiskOrgnummer": "111222333", "orgNavn": "Foo AS"},
                        "brukerSvar": {
                          "erOpplysningeneRiktige": {"svar": true, "sporsmaltekst": "Er opplysningene riktige?", "svartekster": null},
                          "arbeidssituasjon": {"svar": "ARBEIDSTAKER", "sporsmaltekst": "Hva er din arbeidssituasjon?", "svartekster": null}
                        },
                        "tidligereArbeidsgiver": null
                      }
                    }
                """.trimIndent()

                consumer.processRecord(
                    ConsumerRecord(
                        SYKMELDINGSPERIODE_TOPIC,
                        0,
                        0L,
                        "sykmelding-raw",
                        rawJson,
                    ),
                )

                verify(exactly = 1) {
                    repository.storeSykmeldingsperioder(
                        withArg { perioder ->
                            perioder.shouldHaveSize(1)
                            perioder.single().sykmeldtFnr shouldBe "99887766554"
                            perioder.single().organisasjonsnummer shouldBe "111222333"
                            perioder.single().fom shouldBe LocalDate.of(2025, 1, 10)
                            perioder.single().tom shouldBe LocalDate.of(2025, 1, 20)
                        },
                    )
                }
            }
        }
    })

private fun kafkaMessage(
    perioder: List<SykmeldingsperiodeAGDTO>,
): String = configuredJacksonMapper.writeValueAsString(
    SendtSykmeldingKafkaMessage(
        sykmelding = ArbeidsgiverSykmelding(
            sykmeldingsperioder = perioder,
        ),
        kafkaMetadata = KafkaMetadata(
            fnr = "12345678901",
        ),
        event = Event(
            arbeidsgiver = Arbeidsgiver(
                orgnummer = "987654321",
            ),
        ),
    ),
)
