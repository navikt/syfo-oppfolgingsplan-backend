package no.nav.syfo.aareg

import java.math.BigDecimal
import java.time.LocalDate

data class Arbeidsforhold(
    val arbeidssted: Arbeidssted,
    val ansettelsesperiode: Periode,
    val ansettelsesdetaljer: List<Ansettelsesdetaljer> = emptyList(),
)

data class Ansettelsesdetaljer(
    val rapporteringsmaaneder: Periode,
    val yrke: Kodeverksentitet? = null,
    val avtaltStillingsprosent: BigDecimal? = null,
)

data class Kodeverksentitet(
    val kode: String? = null,
    val beskrivelse: String? = null,
)

data class Arbeidssted(
    val identer: List<Ident> = emptyList(),
)

data class Ident(
    val type: String,
    val ident: String,
)

data class Periode(
    val fra: LocalDate? = null,
    val til: LocalDate? = null,
    val sluttdato: LocalDate? = null,
)

data class Stillingsinformasjon(
    val stillingstittel: String? = null,
    val stillingsprosent: BigDecimal? = null,
)
