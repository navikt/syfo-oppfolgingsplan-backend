package no.nav.syfo.istilgangskontroll.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.oppfolgingsplan.api.v1.veilder.NAV_PERSONIDENT_HEADER
import no.nav.syfo.oppfolgingsplan.domain.Fodselsnummer
import no.nav.syfo.util.logger

interface IIsTilgangskontrollClient {
    suspend fun harTilgangTilSykmeldt(
        sykmeldtFnr: Fodselsnummer,
        token: String,
    ): Boolean
}

class IsTilgangskontrollClient(
    private val httpClient: HttpClient,
    private val isTilganskontrollBaseUrl: String,
) : IIsTilgangskontrollClient {
    private val logger = logger()

    override suspend fun harTilgangTilSykmeldt(sykmeldtFnr: Fodselsnummer, token: String): Boolean {
        return try {
            val tilgang = httpClient.get("${isTilganskontrollBaseUrl}/api/tilgang/navident/person") {
                header("Authorization", "Bearer $token")
                header(NAV_PERSONIDENT_HEADER, sykmeldtFnr.value)
                accept(ContentType.Application.Json)
            }
            tilgang.body<Tilgang>().erGodkjent
        } catch (e: ResponseException) {
            if (e.response.status == HttpStatusCode.Forbidden) {
                logger.warn("Access denied")
            } else {
                handleUnexpectedResponseException(e.response)
            }
            false
        }
    }

    private fun handleUnexpectedResponseException(
        response: HttpResponse,
    ) {
        logger.error(
            "Error while requesting access to person from istilgangskontroll with {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
        )
    }
}

class FakeIsTilgangskontrollClient : IIsTilgangskontrollClient {
    override suspend fun harTilgangTilSykmeldt(
        sykmeldtFnr: Fodselsnummer,
        token: String,
    ) = true
}
