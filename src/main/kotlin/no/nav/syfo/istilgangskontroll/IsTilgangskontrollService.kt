package no.nav.syfo.istilgangskontroll

import no.nav.syfo.istilgangskontroll.client.IIsTilgangskontrollClient
import no.nav.syfo.oppfolgingsplan.domain.Fodselsnummer

class IsTilgangskontrollService(
    private val client: IIsTilgangskontrollClient,
) {
    suspend fun harTilgangTilSykmeldt(
        sykmeldtFnr: Fodselsnummer,
        token: String,
    ): Boolean {
        return client.harTilgangTilSykmeldt(
            token = token,
            sykmeldtFnr = sykmeldtFnr,
        )
    }
}
