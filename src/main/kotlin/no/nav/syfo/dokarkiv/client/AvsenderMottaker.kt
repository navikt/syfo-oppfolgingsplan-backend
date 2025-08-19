package no.nav.syfo.dokarkiv.client

data class AvsenderMottaker(
    val id: String,
    val idType: String,
    val navn: String, // Navnet til avsender/mottaker. Skal være på format Fornavn Mellomnavn Etternavn.
)
