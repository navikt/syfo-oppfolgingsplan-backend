package no.nav.syfo.oppfolgingsplan.domain

data class Fodselsnummer(val value: String) {
    private val elevenDigits = Regex("^\\d{11}\$")

    init {
        require(elevenDigits.matches(value))
    }
}
