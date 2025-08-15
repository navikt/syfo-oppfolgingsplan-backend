package no.nav.syfo.pdl

import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.util.logger

class PdlService(private val pdlClient: PdlClient) {

    private val logger = logger()

    suspend fun getNameFor(fnr: String): String? {
        val response = try {
            pdlClient.getPerson(fnr, "token")
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
