package no.nav.syfo.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import no.nav.syfo.aareg.AaregService
import no.nav.syfo.aareg.client.AaregClient
import no.nav.syfo.aareg.client.FakeAaregClient
import no.nav.syfo.aareg.client.IAaregClient
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.LocalEnvironment
import no.nav.syfo.application.NaisEnvironment
import no.nav.syfo.application.database.Database
import no.nav.syfo.application.database.DatabaseConfig
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.isBudstikkaShadowEnabled
import no.nav.syfo.application.isLocalEnv
import no.nav.syfo.application.isProdEnv
import no.nav.syfo.application.kafka.producerProperties
import no.nav.syfo.application.kafka.stringProducerProperties
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.application.valkey.ValkeyCache
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.dinesykmeldte.client.DineSykmeldteHttpClient
import no.nav.syfo.dinesykmeldte.client.FakeDineSykmeldteHttpClient
import no.nav.syfo.dokarkiv.DokarkivService
import no.nav.syfo.dokarkiv.client.DokarkivClient
import no.nav.syfo.dokarkiv.client.FakeDokarkivClient
import no.nav.syfo.dokumentporten.DokumentportenService
import no.nav.syfo.dokumentporten.SendOppfolgingsplanTask
import no.nav.syfo.dokumentporten.client.DokumentportenClient
import no.nav.syfo.dokumentporten.client.FakeDokumentportenClient
import no.nav.syfo.dokumentporten.client.IDokumentportenClient
import no.nav.syfo.isdialogmelding.IsDialogmeldingService
import no.nav.syfo.isdialogmelding.client.FakeIsDialogmeldingClient
import no.nav.syfo.isdialogmelding.client.IsDialogmeldingClient
import no.nav.syfo.istilgangskontroll.IsTilgangskontrollService
import no.nav.syfo.istilgangskontroll.client.FakeIsTilgangskontrollClient
import no.nav.syfo.istilgangskontroll.client.IsTilgangskontrollClient
import no.nav.syfo.oppfolgingsplan.service.OppfolgingsplanService
import no.nav.syfo.oppfolgingsplan.service.PaaminnelseService
import no.nav.syfo.oppfolgingsplan.task.CleanupUtkastTask
import no.nav.syfo.oppfolgingsplan.task.SoftDeleteOppfolgingsplanerTask
import no.nav.syfo.pdfgen.PdfGenService
import no.nav.syfo.pdfgen.client.PdfGenClient
import no.nav.syfo.pdl.PdlService
import no.nav.syfo.pdl.client.FakePdlClient
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.sykmelding.db.SykmeldingsperiodeRepository
import no.nav.syfo.sykmelding.kafka.SykmeldingsperiodeConsumer
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.util.httpClientDefault
import no.nav.syfo.varsel.EsyfovarselProducer
import no.nav.syfo.varsel.budstikka.BudstikkaProducer
import no.nav.syfo.varsel.budstikka.BudstikkaPublisher
import no.nav.syfo.varsel.budstikka.NoOpBudstikkaPublisher
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
            clientsModule(),
            databaseModule(),
            valkeyModule(),
            servicesModule(),
            kafkeProducerModule(),
        )
    }
}

private fun applicationStateModule() = module { single { ApplicationState() } }

private fun environmentModule(isLocalEnv: Boolean) = module {
    single {
        if (isLocalEnv) {
            LocalEnvironment()
        } else {
            NaisEnvironment()
        }
    }
}

private fun clientsModule() = module {
    single { httpClientDefault() }
    single { PdfGenClient(get(), env().pdfGenUrl) }
    single { TexasHttpClient(get(), env().texas) }
    single<IAaregClient> {
        if (isLocalEnv()) {
            FakeAaregClient()
        } else {
            AaregClient(
                httpClient = get(),
                aaregBaseUrl = env().aaregBaseUrl,
                texasHttpClient = get(),
                scope = env().aaregScope,
            )
        }
    }
    single {
        if (isLocalEnv()) {
            FakeDokarkivClient()
        } else {
            DokarkivClient(
                dokarkivBaseUrl = env().dokarkivBaseUrl,
                texasHttpClient = get(),
                scope = env().dokarkivScope,
                httpClient = get(),
            )
        }
    }
    single {
        if (isLocalEnv()) {
            FakePdlClient()
        } else {
            PdlClient(
                httpClient = get(),
                pdlBaseUrl = env().pdlBaseUrl,
                texasHttpClient = get(),
                scope = env().pdlScope,
            )
        }
    }
    single {
        if (isLocalEnv()) {
            FakeIsDialogmeldingClient()
        } else {
            IsDialogmeldingClient(
                get(),
                env().isDialogmeldingBaseUrl,
            )
        }
    }
    single {
        if (isLocalEnv()) {
            FakeDineSykmeldteHttpClient()
        } else {
            DineSykmeldteHttpClient(
                httpClient = get(),
                dineSykmeldteBaseUrl = env().dineSykmeldteBaseUrl,
            )
        }
    }
    single {
        if (isLocalEnv()) {
            FakeDokumentportenClient()
        } else {
            DokumentportenClient(
                dokumentportenBaseUrl = env().dokumentportenBaseUrl,
                texasHttpClient = get(),
                scope = env().dokumentportenScope,
                httpClient = get(),
            ) as IDokumentportenClient
        }
    }
    single {
        if (isLocalEnv()) {
            FakeIsTilgangskontrollClient()
        } else {
            IsTilgangskontrollClient(
                get(),
                env().isTilgangskontrollBaseUrl,
            )
        }
    }
}

private fun databaseModule() = module {
    single<DatabaseInterface> {
        Database(
            DatabaseConfig(
                jdbcUrl = env().database.jdbcUrl(),
                username = env().database.username,
                password = env().database.password,
            ),
        )
    }
}

private fun valkeyModule() = module {
    single {
        ValkeyCache(env().valkeyEnvironment)
    }
}

private fun kafkeProducerModule() = module {
    single {
        EsyfovarselProducer(
            KafkaProducer<String, EsyfovarselHendelse>(
                producerProperties(env().kafka),
            ),
        )
    }
    single<BudstikkaPublisher> {
        if (isBudstikkaShadowEnabled(env().budstikkaEnabled, isProdEnv())) {
            BudstikkaProducer(
                KafkaProducer<String, String>(
                    stringProducerProperties(env().kafka),
                ),
            )
        } else {
            NoOpBudstikkaPublisher
        }
    }
}

private fun servicesModule() = module {
    single { DokumentportenService(get(), get(), get(), get()) }
    single { DineSykmeldteService(get(), get()) }
    single { DokarkivService(get()) }
    single { IsDialogmeldingService(get()) }
    single { IsTilgangskontrollService(get()) }
    single { LeaderElection(get(), env().electorPath) }
    single { PdlService(get()) }
    single { AaregService(get()) }
    single {
        OppfolgingsplanService(
            database = get(),
            esyfovarselProducer = get(),
            budstikkaPublisher = get(),
            pdlService = get(),
            aaregService = get(),
        )
    }
    single { PaaminnelseService(database = get(), sykmeldingsperiodeRepository = get()) }
    single { PdfGenService(get(), get()) }
    single { SendOppfolgingsplanTask(get(), get()) }
    single { CleanupUtkastTask(get(), get()) }
    single {
        SoftDeleteOppfolgingsplanerTask(
            leaderElection = get(),
            oppfolgingsplanService = get(),
            interval = SoftDeleteOppfolgingsplanerTask.intervalForEnvironment(isProdEnv()),
        )
    }
    single { SykmeldingsperiodeRepository(get()) }
    single { SykmeldingsperiodeConsumer(get(), env().kafka) }
}

private fun Scope.env() = get<Environment>()
