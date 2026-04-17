package no.nav.syfo.aareg

import no.nav.syfo.aareg.client.IAaregClient
import no.nav.syfo.util.logger

class AaregService(
    private val aaregClient: IAaregClient,
) {
    private val logger = logger()

    suspend fun getStillingsinformasjon(
        fnr: String,
        virksomhetsnummer: String,
    ): Stillingsinformasjon? {
        val arbeidsforhold = aaregClient.getArbeidsforhold(fnr)

        val matchendeArbeidsforhold = arbeidsforhold
            .asSequence()
            .filter { arbeidsforhold ->
                arbeidsforhold.arbeidssted.identer.any { ident ->
                    ident.type == ORGANISASJONSNUMMER && ident.ident == virksomhetsnummer
                }
            }
            .filter { arbeidsforhold ->
                arbeidsforhold.ansettelsesperiode.sluttdato == null
            }
            .mapNotNull { arbeidsforhold ->
                arbeidsforhold.ansettelsesdetaljer
                    .firstOrNull { detalj -> detalj.rapporteringsmaaneder.til == null }
            }
            .toList()

        if (matchendeArbeidsforhold.size > 1) {
            logger.warn(
                "Fant flere matchende arbeidsforhold i Aareg for virksomhetsnummer $virksomhetsnummer, bruker første treff",
            )
        }

        return matchendeArbeidsforhold.firstOrNull()?.let { detalj ->
            Stillingsinformasjon(
                stillingstittel = detalj.yrke?.beskrivelse,
                stillingsprosent = detalj.avtaltStillingsprosent,
            )
        }
    }

    private companion object {
        const val ORGANISASJONSNUMMER = "ORGANISASJONSNUMMER"
    }
}
