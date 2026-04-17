package no.nav.syfo.aareg

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.aareg.client.IAaregClient
import java.math.BigDecimal
import java.time.YearMonth

class AaregServiceTest :
    DescribeSpec({
        val aaregClient = mockk<IAaregClient>()
        val service = AaregService(aaregClient)

        describe("getStillingsinformasjon") {
            it("returns stillingsinformasjon for active arbeidsforhold in matching virksomhet") {
                coEvery { aaregClient.getArbeidsforhold("12345678901") } returns listOf(
                    Arbeidsforhold(
                        arbeidssted = Arbeidssted(
                            identer = listOf(
                                Ident(type = "ORGANISASJONSNUMMER", ident = "999999999"),
                            ),
                        ),
                        ansettelsesperiode = Ansettelsesperiode(sluttdato = null),
                        ansettelsesdetaljer = listOf(
                            Ansettelsesdetaljer(
                                rapporteringsmaaneder = Rapporteringsperiode(
                                    fra = YearMonth.of(2020, 1),
                                    til = null,
                                ),
                                yrke = Kodeverksentitet(beskrivelse = "Systemutvikler"),
                                avtaltStillingsprosent = BigDecimal("80.50"),
                            ),
                        ),
                    ),
                )

                val result = service.getStillingsinformasjon("12345678901", "999999999")

                result shouldNotBe null
                result?.stillingstittel shouldBe "Systemutvikler"
                result?.stillingsprosent shouldBe BigDecimal("80.50")
            }

            it("returns null when no arbeidsforhold matches virksomhetsnummer") {
                coEvery { aaregClient.getArbeidsforhold("12345678901") } returns listOf(
                    Arbeidsforhold(
                        arbeidssted = Arbeidssted(
                            identer = listOf(
                                Ident(type = "ORGANISASJONSNUMMER", ident = "111111111"),
                            ),
                        ),
                        ansettelsesperiode = Ansettelsesperiode(sluttdato = null),
                        ansettelsesdetaljer = listOf(
                            Ansettelsesdetaljer(
                                rapporteringsmaaneder = Rapporteringsperiode(
                                    fra = YearMonth.of(2020, 1),
                                    til = null,
                                ),
                                yrke = Kodeverksentitet(beskrivelse = "Sykepleier"),
                                avtaltStillingsprosent = BigDecimal("50.00"),
                            ),
                        ),
                    ),
                )

                service.getStillingsinformasjon("12345678901", "999999999").shouldBeNull()
            }

            it("returns null stillingstittel when yrke beskrivelse is blank") {
                coEvery { aaregClient.getArbeidsforhold("12345678901") } returns listOf(
                    Arbeidsforhold(
                        arbeidssted = Arbeidssted(
                            identer = listOf(
                                Ident(type = "ORGANISASJONSNUMMER", ident = "999999999"),
                            ),
                        ),
                        ansettelsesperiode = Ansettelsesperiode(sluttdato = null),
                        ansettelsesdetaljer = listOf(
                            Ansettelsesdetaljer(
                                rapporteringsmaaneder = Rapporteringsperiode(
                                    fra = YearMonth.of(2020, 1),
                                    til = null,
                                ),
                                yrke = Kodeverksentitet(beskrivelse = "   "),
                                avtaltStillingsprosent = BigDecimal("80.50"),
                            ),
                        ),
                    ),
                )

                val result = service.getStillingsinformasjon("12345678901", "999999999")

                result shouldNotBe null
                result?.stillingstittel.shouldBeNull()
                result?.stillingsprosent shouldBe BigDecimal("80.50")
            }

            it("uses first match when multiple active arbeidsforhold match virksomhetsnummer") {
                coEvery { aaregClient.getArbeidsforhold("12345678901") } returns listOf(
                    Arbeidsforhold(
                        arbeidssted = Arbeidssted(
                            identer = listOf(
                                Ident(type = "ORGANISASJONSNUMMER", ident = "999999999"),
                            ),
                        ),
                        ansettelsesperiode = Ansettelsesperiode(sluttdato = null),
                        ansettelsesdetaljer = listOf(
                            Ansettelsesdetaljer(
                                rapporteringsmaaneder = Rapporteringsperiode(
                                    fra = YearMonth.of(2020, 1),
                                    til = null,
                                ),
                                yrke = Kodeverksentitet(beskrivelse = "Første stilling"),
                                avtaltStillingsprosent = BigDecimal("100.00"),
                            ),
                        ),
                    ),
                    Arbeidsforhold(
                        arbeidssted = Arbeidssted(
                            identer = listOf(
                                Ident(type = "ORGANISASJONSNUMMER", ident = "999999999"),
                            ),
                        ),
                        ansettelsesperiode = Ansettelsesperiode(sluttdato = null),
                        ansettelsesdetaljer = listOf(
                            Ansettelsesdetaljer(
                                rapporteringsmaaneder = Rapporteringsperiode(
                                    fra = YearMonth.of(2020, 2),
                                    til = null,
                                ),
                                yrke = Kodeverksentitet(beskrivelse = "Andre stilling"),
                                avtaltStillingsprosent = BigDecimal("20.00"),
                            ),
                        ),
                    ),
                )

                val result = service.getStillingsinformasjon("12345678901", "999999999")

                result shouldBe Stillingsinformasjon(
                    stillingstittel = "Første stilling",
                    stillingsprosent = BigDecimal("100.00"),
                )
            }
        }
    })
