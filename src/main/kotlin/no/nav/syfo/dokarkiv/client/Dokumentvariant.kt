package no.nav.syfo.dokarkiv.client

data class Dokumentvariant(
    val filnavn: String,
    val filtype: String,
    val fysiskDokument: ByteArray,
    val variantformat: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Dokumentvariant

        if (filnavn != other.filnavn) return false
        if (filtype != other.filtype) return false
        if (!fysiskDokument.contentEquals(other.fysiskDokument)) return false
        if (variantformat != other.variantformat) return false

        return true
    }

    override fun hashCode(): Int {
        var result = filnavn.hashCode()
        result = 31 * result + filtype.hashCode()
        result = 31 * result + fysiskDokument.contentHashCode()
        result = 31 * result + variantformat.hashCode()
        return result
    }
}
