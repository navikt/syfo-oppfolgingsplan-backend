package no.nav.syfo.dinesykmeldte

class DineSykmeldteService(
    private val dineSykmeldteHttpClient: DineSykmeldteHttpClient
) {
    suspend fun getSykmeldtForNarmesteleder(
        narmestelederId: String,
    ): Sykmeldt? {
        // TODO: Use Valkey cache to avoid multiple calls to dinesykmeldte-backend
        return dineSykmeldteHttpClient.getSykmeldtForNarmesteLederId(narmestelederId)
    }
}