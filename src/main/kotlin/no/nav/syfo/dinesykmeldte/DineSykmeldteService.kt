package no.nav.syfo.dinesykmeldte


import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import no.nav.syfo.util.logger


class DineSykmeldteService(
    private val dineSykmeldteHttpClient: DineSykmeldteHttpClient
) {
    private val logger = logger()

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