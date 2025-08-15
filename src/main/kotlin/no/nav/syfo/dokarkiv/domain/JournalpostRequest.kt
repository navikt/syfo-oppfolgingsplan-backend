package no.nav.syfo.dokarkiv.domain

data class JournalpostRequest(
    val avsenderMottaker: AvsenderMottaker,
    val tittel: String,
    val bruker: Bruker,
    val dokumenter: List<Dokument>,
    val journalfoerendeEnhet: Int,
    val journalpostType: String,
    val kanal: String,
    val sak: Sak,
    val tema: String,
    val eksternReferanseId: String,
    // By default, user can not see documents created by others. This one enables viewing on dokarkiv page
    val overstyrInnsynsregler: String = "VISES_MASKINELT_GODKJENT",
)
