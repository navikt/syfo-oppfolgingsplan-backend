package no.nav.syfo.isdialogmelding

import no.nav.syfo.isdialogmelding.client.IIsDialogmeldingClient

class IsDialogmeldingService(
    private val client: IIsDialogmeldingClient,
) {
    suspend fun sendOppfolgingsplanToGeneralPractitioner(
        token: String,
        sykmeldtFnr: String,
        plansAsPdf: ByteArray,
    ) {
        client.sendOppfolgingsplanToGeneralPractitioner(
            token = token,
            sykmeldtFnr = sykmeldtFnr,
            plansAsPdf = plansAsPdf,
        )
    }
}
