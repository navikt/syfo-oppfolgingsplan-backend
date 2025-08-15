package no.nav.syfo.dokarkiv.domain

data class AvsenderMottaker(
    val id: String,
    val idType: String,
    val navn: String, // Navnet til avsender/mottaker. Skal være på format Fornavn Mellomnavn Etternavn.
)
