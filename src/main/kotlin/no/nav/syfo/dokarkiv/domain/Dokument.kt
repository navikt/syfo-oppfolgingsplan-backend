package no.nav.syfo.dokarkiv.domain

data class Dokument(
    val brevkode: String,
    val dokumentKategori: String,
    val dokumentvarianter: List<Dokumentvariant>,
    val tittel: String,
)
