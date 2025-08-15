package no.nav.syfo.dokarkiv

import no.nav.syfo.dokarkiv.domain.AvsenderMottaker
import no.nav.syfo.dokarkiv.domain.Bruker
import no.nav.syfo.dokarkiv.domain.Dokument
import no.nav.syfo.dokarkiv.domain.Dokumentvariant
import no.nav.syfo.dokarkiv.domain.JournalpostRequest
import no.nav.syfo.dokarkiv.domain.Sak
import no.nav.syfo.oppfolgingsplan.db.PersistedOppfolgingsplan

class DokarkivService(
    private val dokarkivClient: IDokarkivClient,
) {
    suspend fun arkiverOppfolginsplan(
        oppfolginsplan: PersistedOppfolgingsplan,
        pdf: ByteArray,
    ): String {
        val avsenderMottaker = createAvsenderMottaker(
            oppfolginsplan.organisasjonsnummer,
            oppfolginsplan.organisasjonsnavn ?: "Arbeidsgiver"
        )

        val journalpostRequest = createJournalpostRequest(
            fnr = oppfolginsplan.sykmeldtFnr,
            pdf = pdf,
            arbeidsgiverNavn = oppfolginsplan.organisasjonsnavn ?: "Arbeidsgiver",
            avsenderMottaker = avsenderMottaker,
            uuid = oppfolginsplan.uuid.toString(),
        )
        return dokarkivClient.sendJournalpostRequestToDokarkiv(journalpostRequest)
    }

    private fun createAvsenderMottaker(
        orgnummer: String,
        virksomhetsnavn: String,
    ) = AvsenderMottaker(
        id = orgnummer,
        idType = ID_TYPE_ORGNR,
        navn = virksomhetsnavn,
    )

    @Suppress("LongParameterList")
    private fun createJournalpostRequest(
        fnr: String,
        pdf: ByteArray,
        arbeidsgiverNavn: String,
        avsenderMottaker: AvsenderMottaker,
        uuid: String,
    ): JournalpostRequest {
        val dokumentnavn = "Oppfølgingsplan $arbeidsgiverNavn"
        return JournalpostRequest(
            tema = TEMA_OPP,
            tittel = dokumentnavn,
            journalfoerendeEnhet = JOURNALFORENDE_ENHET,
            journalpostType = JOURNALPOST_TYPE,
            kanal = KANAL,
            sak = Sak(sakstype = SAKSTYPE_GENERELL_SAK),
            avsenderMottaker = avsenderMottaker,
            bruker = Bruker(
                id = fnr,
                idType = FNR_TYPE,
            ),
            dokumenter = makeDokumenter(dokumentnavn, pdf),
            eksternReferanseId = uuid,
        )
    }

    private fun makeDokumenter(
        dokumentNavn: String,
        dokumentPdf: ByteArray,
    ) = listOf(
        Dokument(
            dokumentKategori = DOKUMENT_KATEGORY_ES,
            brevkode = BREV_KODE_TYPE_OPPF_PLA,
            tittel = dokumentNavn,
            dokumentvarianter = listOf(
                Dokumentvariant(
                    filnavn = dokumentNavn,
                    filtype = FILE_TYPE_PDFA,
                    variantformat = FORMAT_TYPE_ARKIV,
                    fysiskDokument = dokumentPdf,
                )
            )
        )
    )

    companion object {
        const val ID_TYPE_ORGNR = "ORGNR"
        const val TEMA_OPP = "OPP"
        const val SAKSTYPE_GENERELL_SAK = "GENERELL_SAK"
        const val FNR_TYPE = "FNR"
        const val FILE_TYPE_PDFA = "PDFA"
        const val FORMAT_TYPE_ARKIV = "ARKIV"
        const val DOKUMENT_KATEGORY_ES = "ES"
        const val BREV_KODE_TYPE_OPPF_PLA = "OPPF_PLA"
        const val JOURNALFORENDE_ENHET = 9999
        const val JOURNALPOST_TYPE = "INNGAAENDE"
        const val KANAL = "NAV_NO"
    }
}
