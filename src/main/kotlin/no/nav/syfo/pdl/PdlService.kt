package no.nav.syfo.pdl

import no.nav.syfo.pdl.client.IPdlClient
import no.nav.syfo.util.logger

class PdlService(private val pdlClient: IPdlClient) {

    private val logger = logger()

    suspend fun getNameFor(fnr: String): String? {
        val response = try {
            pdlClient.getPerson(fnr)
        } catch (e: Exception) {
            logger.error("Could not fetch person from PDL", e)
            return null
        }
        val navn = response.data.person?.navn?.firstOrNull()

        val (fornavn, mellomnavn, etternavn) = navn
            ?: return null

        return listOfNotNull(fornavn, mellomnavn, etternavn)
            .joinToString(" ")
            .ifBlank { null }
    }
}
