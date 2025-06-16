package no.nav.syfo.dinesykmeldte


import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import org.slf4j.LoggerFactory


internal val logger = LoggerFactory.getLogger("no.nav.syfo.dinesykmeldte")

class DineSykmeldteService(
    private val dineSykmeldteHttpClient: DineSykmeldteHttpClient
) {
    suspend fun getSykmeldtForNarmesteleder(
        narmestelederId: String,
        accessToken: String
    ): Sykmeldt? {
        // TODO: Use Valkey cache to avoid multiple calls to dinesykmeldte-backend
        return try {
            dineSykmeldteHttpClient.getSykmeldtForNarmesteLederId(narmestelederId, accessToken)
        } catch (clientRequestException: ClientRequestException) {
            when (clientRequestException.response.status) {
                HttpStatusCode.NotFound -> {
                    logger.warn("Sykmeldt not found for narmestelederId: $narmestelederId")
                    null
                }
                else -> throw RuntimeException("Error while fetching sykmeldt", clientRequestException)
            }
        }
    }
}