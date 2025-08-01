package no.nav.syfo.isdialogmelding

class IsDialogmeldingService(
    private val client: IsDialogmeldingClient,
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
