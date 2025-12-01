package no.nav.syfo.dinesykmeldte


import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import no.nav.syfo.application.valkey.COUNT_CACHE_MISS_DINE_SYKMELDTE
import no.nav.syfo.application.valkey.ValkeyCache
import no.nav.syfo.dinesykmeldte.client.IDineSykmeldteHttpClient
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.util.logger


class DineSykmeldteService(
    private val dineSykmeldteHttpClient: IDineSykmeldteHttpClient,
    private val valkeyCache: ValkeyCache
) {
    private val logger = logger()

    suspend fun getSykmeldtForNarmesteleder(
        narmestelederId: String,
        lederFnr: String,
        accessToken: String
    ): Sykmeldt? {
        valkeyCache.getSykmeldt(lederFnr, narmestelederId)?.let { cachedSykmeldt ->
            return cachedSykmeldt
        }
        COUNT_CACHE_MISS_DINE_SYKMELDTE.increment()
        return try {
            val sykmeldt = dineSykmeldteHttpClient.getSykmeldtForNarmesteLederId(narmestelederId, accessToken)
            valkeyCache.putSykmeldt(lederFnr, narmestelederId, sykmeldt)
            return sykmeldt
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
