package no.nav.syfo.isdialogmelding

class IsDialogmeldingService(
    private val client: IsDialogmeldingClient,
) {
    suspend fun sendLpsPlanToGeneralPractitioner(
        token: String,
        sykmeldtFnr: String,
        plansAsPdf: ByteArray,
    ) {
        client.sendLpsPlanToGeneralPractitioner(
            token = token,
            sykmeldtFnr = sykmeldtFnr,
            plansAsPdf = plansAsPdf,
        )
    }
}
