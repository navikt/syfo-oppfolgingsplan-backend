package no.nav.syfo.dinesykmeldte.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import java.util.Random
import net.datafaker.Faker

fun interface IDineSykmeldteHttpClient {
    suspend fun getSykmeldtForNarmesteLederId(
        narmestelederId: String,
        token: String,
    ): Sykmeldt
}

class DineSykmeldteHttpClient(
    private val httpClient: HttpClient,
    private val dineSykmeldteBaseUrl: String,
) : IDineSykmeldteHttpClient {
    override suspend fun getSykmeldtForNarmesteLederId(
        narmestelederId: String,
        token: String,
    ): Sykmeldt {
        return httpClient
            .get("$dineSykmeldteBaseUrl/api/v2/dinesykmeldte/$narmestelederId") {
                header("Authorization", "Bearer $token")
            }
            .body<Sykmeldt>()
    }
}

class FakeDineSykmeldteHttpClient : IDineSykmeldteHttpClient {
    override suspend fun getSykmeldtForNarmesteLederId(
        narmestelederId: String,
        token: String,
    ): Sykmeldt {
        val faker = Faker(Random(narmestelederId.hashCode().toLong()))
        return Sykmeldt(
            narmestelederId = narmestelederId,
            orgnummer = faker.numerify("#########"),
            fnr = faker.numerify("###########"),
            navn = faker.name().fullName(),
            sykmeldinger = listOf(
                DineSykmeldteSykmelding(
                    arbeidsgiver = faker.company().name()
                )
            ),
            aktivSykmelding = true
        )
    }
}

data class Sykmeldt(
    val narmestelederId: String,
    val orgnummer: String,
    val fnr: String,
    val navn: String,
    val sykmeldinger: List<DineSykmeldteSykmelding>?,
    val aktivSykmelding: Boolean?,
)

data class DineSykmeldteSykmelding(
    // respons inneholder mer data, men vi trenger kun dette feltet
    val arbeidsgiver: String,
)

fun Sykmeldt.getOrganizationName(): String? {
    return sykmeldinger?.firstOrNull()?.arbeidsgiver
}
