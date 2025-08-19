package no.nav.syfo.isdialogmelding.client

data class OppfolgingsplanDialogmelding(
    val sykmeldtFnr: String,
    val oppfolgingsplanPdf: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OppfolgingsplanDialogmelding

        if (sykmeldtFnr != other.sykmeldtFnr) return false
        if (!oppfolgingsplanPdf.contentEquals(other.oppfolgingsplanPdf)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sykmeldtFnr.hashCode()
        result = 31 * result + oppfolgingsplanPdf.contentHashCode()
        return result
    }
}
