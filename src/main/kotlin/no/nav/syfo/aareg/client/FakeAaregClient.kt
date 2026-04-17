package no.nav.syfo.aareg.client

import net.datafaker.Faker
import no.nav.syfo.aareg.Ansettelsesdetaljer
import no.nav.syfo.aareg.Ansettelsesperiode
import no.nav.syfo.aareg.Arbeidsforhold
import no.nav.syfo.aareg.Arbeidssted
import no.nav.syfo.aareg.Ident
import no.nav.syfo.aareg.Kodeverksentitet
import no.nav.syfo.aareg.Rapporteringsperiode
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Random

class FakeAaregClient : IAaregClient {
    override suspend fun getArbeidsforhold(fnr: String): List<Arbeidsforhold> {
        val faker = Faker(Random(fnr.toLong()))
        val stillingsprosent = BigDecimal.valueOf(faker.number().randomDouble(2, 20, 100))
            .setScale(2, RoundingMode.HALF_UP)

        return listOf(
            Arbeidsforhold(
                arbeidssted = Arbeidssted(
                    identer = listOf(
                        Ident(type = "ORGANISASJONSNUMMER", ident = "orgnummer"),
                        Ident(type = "ORGANISASJONSNUMMER", ident = "123456789"),
                    ),
                ),
                ansettelsesperiode = Ansettelsesperiode(sluttdato = null),
                ansettelsesdetaljer = listOf(
                    Ansettelsesdetaljer(
                        rapporteringsmaaneder = Rapporteringsperiode(til = null),
                        yrke = Kodeverksentitet(
                            kode = faker.job().field(),
                            beskrivelse = faker.job().title(),
                        ),
                        avtaltStillingsprosent = stillingsprosent,
                    ),
                ),
            ),
        )
    }
}
