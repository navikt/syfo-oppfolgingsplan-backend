package no.nav.syfo.dinesykmeldte

import java.util.Random
import net.datafaker.Faker

class FakeDineSykmeldteHttpClient() : IDineSykmeldteHttpClient {
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
            aktivSykmelding = true
        )
    }
}
