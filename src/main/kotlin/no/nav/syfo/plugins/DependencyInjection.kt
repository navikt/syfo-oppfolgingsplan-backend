package no.nav.syfo.plugins

import no.nav.syfo.isdialogmelding.IsDialogmeldingClient
import io.ktor.server.application.Application
import io.ktor.server.application.install
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.LocalEnvironment
import no.nav.syfo.application.NaisEnvironment
import no.nav.syfo.application.database.Database
import no.nav.syfo.application.database.DatabaseConfig
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.isLocalEnv
import no.nav.syfo.application.kafka.producerProperties
import no.nav.syfo.dinesykmeldte.DineSykmeldteHttpClient
import no.nav.syfo.dinesykmeldte.FakeDineSykmeldteHttpClient
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.dokarkiv.DokarkivClient
import no.nav.syfo.dokarkiv.FakeDokarkivClient
import no.nav.syfo.dokarkiv.DokarkivService
import no.nav.syfo.isdialogmelding.FakeIsDialogmeldingClient
import no.nav.syfo.isdialogmelding.IsDialogmeldingService
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.pdfgen.PdfGenClient
import no.nav.syfo.pdfgen.PdfGenService
import no.nav.syfo.pdl.PdlService
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.util.httpClientDefault
import no.nav.syfo.varsel.EsyfovarselProducer
import no.nav.syfo.varsel.domain.EsyfovarselHendelse
import org.apache.kafka.clients.producer.KafkaProducer
import org.koin.core.scope.Scope
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureDependencies() {
    install(Koin) {
        slf4jLogger()

        modules(
            applicationStateModule(),
            environmentModule(isLocalEnv()),
            httpClient(),
            databaseModule(),
            servicesModule(),
        )
    }
}

private fun applicationStateModule() = module { single { ApplicationState() } }

private fun environmentModule(isLocalEnv: Boolean) = module {
    single {
        if (isLocalEnv) LocalEnvironment()
        else NaisEnvironment()
    }
}

private fun httpClient() = module {
    single {
        httpClientDefault()
    }
}

private fun databaseModule() = module {
    single<DatabaseInterface> {
        Database(
            DatabaseConfig(
                jdbcUrl = env().database.jdbcUrl(),
                username = env().database.username,
                password = env().database.password,
            )
        )
    }
}

private fun servicesModule() = module {
    single {
        val dineSykmeldteHttpClient =
            if (isLocalEnv()) FakeDineSykmeldteHttpClient() else DineSykmeldteHttpClient(
                httpClient = get(), dineSykmeldteBaseUrl = env().dineSykmeldteBaseUrl
            )
        DineSykmeldteService(dineSykmeldteHttpClient)
    }
    single { TexasHttpClient(get(), env().texas) }
    single { OppfolgingsplanService(database = get(), esyfovarselProducer = get()) }
    single {
        EsyfovarselProducer(
            KafkaProducer<String, EsyfovarselHendelse>(
                producerProperties(env().kafka)
            )
        )
    }
    single { PdfGenClient(get(), env().pdfGenUrl) }
    single {
        if (isLocalEnv()) FakeDokarkivClient() else DokarkivClient(
            dokarkivBaseUrl = env().dokarkivBaseUrl,
            texasHttpClient = get(),
            scope = env().dokarkivScope,
            httpClient = get()
        )
    }

    single { PdfGenService(get(), get(), get()) }
    single {
        if (isLocalEnv()) FakeIsDialogmeldingClient() else
            IsDialogmeldingClient(
                get(),
                env().isDialogmeldingBaseUrl
            )
    }
    single { IsDialogmeldingService(get()) }
    single { DokarkivService(get()) }
    single { PdlService(PdlClient(get(), env().pdlBaseUrl)) }
}

private fun Scope.env() = get<Environment>()
