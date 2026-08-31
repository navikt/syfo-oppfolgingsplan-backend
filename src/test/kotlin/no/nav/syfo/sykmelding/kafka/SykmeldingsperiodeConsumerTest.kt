package no.nav.syfo.sykmelding.kafka

import com.fasterxml.jackson.core.JsonProcessingException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
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
        lateinit var metricsRegistry: SimpleMeterRegistry
        lateinit var consumer: SykmeldingsperiodeConsumer

        beforeTest {
            clearAllMocks(currentThreadOnly = true)
            metricsRegistry = SimpleMeterRegistry()
            consumer = SykmeldingsperiodeConsumer(
                sykmeldingsperiodeRepository = repository,
                kafkaEnv = KafkaEnv.createForLocal(),
                clock = fixedClock,
                recordMetrics = SykmeldingsperiodeRecordMetrics(metricsRegistry),
            )
        }

        fun terminallyRejectedCount(reason: String): Double = metricsRegistry
            .get(SYKMELDING_TERMINALLY_REJECTED_RECORDS_METRIC)
            .tag("reason", reason)
            .counter()
            .count()

        fun retryBatchAttemptCount(): Double = metricsRegistry
            .get(SYKMELDING_DESERIALIZATION_RETRY_BATCH_ATTEMPTS_METRIC)
            .counter()
            .count()

        describe("processBatch") {
            it("records deserialization rejection only after a mixed batch is committed") {
                every { repository.storeSykmeldingsperioder(any()) } returns 1
                var commits = 0

                consumer.processBatch(
                    records = listOf(
                        ConsumerRecord(
                            SYKMELDINGSPERIODE_TOPIC,
                            0,
                            10L,
                            "invalid",
                            "{invalid-json}",
                        ),
                        ConsumerRecord(
                            SYKMELDINGSPERIODE_TOPIC,
                            0,
                            11L,
                            "valid",
                            kafkaMessage(
                                perioder = listOf(
                                    SykmeldingsperiodeAGDTO(
                                        fom = LocalDate.of(2025, 1, 1),
                                        tom = LocalDate.of(2025, 1, 31),
                                    ),
                                ),
                            ),
                        ),
                    ),
                    commitOffsets = { commits++ },
                )

                commits shouldBe 1
                terminallyRejectedCount("deserialization") shouldBe 1.0
                terminallyRejectedCount("invalid_tombstone") shouldBe 0.0
                retryBatchAttemptCount() shouldBe 0.0
            }

            it("records a retry attempt and does not commit when every record fails deserialization") {
                var commits = 0

                shouldThrow<IllegalStateException> {
                    consumer.processBatch(
                        records = listOf(
                            ConsumerRecord(
                                SYKMELDINGSPERIODE_TOPIC,
                                0,
                                20L,
                                "invalid-1",
                                "{invalid-json}",
                            ),
                            ConsumerRecord(
                                SYKMELDINGSPERIODE_TOPIC,
                                0,
                                21L,
                                "invalid-2",
                                "{also-invalid-json}",
                            ),
                        ),
                        commitOffsets = { commits++ },
                    )
                }

                commits shouldBe 0
                terminallyRejectedCount("deserialization") shouldBe 0.0
                retryBatchAttemptCount() shouldBe 1.0
            }

            it("does not record a terminal rejection when offset commit fails") {
                every { repository.storeSykmeldingsperioder(any()) } returns 1

                shouldThrow<IllegalStateException> {
                    consumer.processBatch(
                        records = listOf(
                            ConsumerRecord(
                                SYKMELDINGSPERIODE_TOPIC,
                                0,
                                30L,
                                "invalid",
                                "{invalid-json}",
                            ),
                            ConsumerRecord(
                                SYKMELDINGSPERIODE_TOPIC,
                                0,
                                31L,
                                "valid",
                                kafkaMessage(
                                    perioder = listOf(
                                        SykmeldingsperiodeAGDTO(
                                            fom = LocalDate.of(2025, 2, 1),
                                            tom = LocalDate.of(2025, 2, 28),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        commitOffsets = { error("commit failed") },
                    )
                }

                terminallyRejectedCount("deserialization") shouldBe 0.0
                retryBatchAttemptCount() shouldBe 0.0
            }

            it("records an invalid tombstone only after its offset is committed") {
                var commits = 0

                consumer.processBatch(
                    records = listOf(
                        ConsumerRecord(
                            SYKMELDINGSPERIODE_TOPIC,
                            0,
                            40L,
                            null,
                            null,
                        ),
                    ),
                    commitOffsets = { commits++ },
                )

                commits shouldBe 1
                terminallyRejectedCount("deserialization") shouldBe 0.0
                terminallyRejectedCount("invalid_tombstone") shouldBe 1.0
            }

            it("exposes only bounded rejection reasons and no record-derived labels") {
                metricsRegistry.find(SYKMELDING_TERMINALLY_REJECTED_RECORDS_METRIC)
                    .counters()
                    .map { counter -> counter.id.tags.associate { it.key to it.value } }
                    .toSet() shouldBe setOf(
                    mapOf("reason" to "deserialization"),
                    mapOf("reason" to "invalid_tombstone"),
                )
                metricsRegistry.get(SYKMELDING_DESERIALIZATION_RETRY_BATCH_ATTEMPTS_METRIC)
                    .counter()
                    .id
                    .tags
                    .isEmpty() shouldBe true
            }

            it("exports stable Prometheus counter names and reason labels") {
                val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
                val recordMetrics = SykmeldingsperiodeRecordMetrics(prometheusRegistry)
                recordMetrics.recordTerminallyRejected(
                    SykmeldingsperiodeRejectionReason.DESERIALIZATION,
                    1,
                )
                recordMetrics.recordTerminallyRejected(
                    SykmeldingsperiodeRejectionReason.INVALID_TOMBSTONE,
                    1,
                )
                recordMetrics.recordDeserializationRetryBatchAttempt()

                val scrape = prometheusRegistry.scrape()
                scrape shouldContain
                    """syfo_oppfolgingsplan_backend_sykmelding_terminally_rejected_records_total{reason="deserialization"} 1.0"""
                scrape shouldContain
                    """syfo_oppfolgingsplan_backend_sykmelding_terminally_rejected_records_total{reason="invalid_tombstone"} 1.0"""
                scrape shouldContain
                    "syfo_oppfolgingsplan_backend_sykmelding_deserialization_retry_batch_attempts_total 1.0"
            }
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
