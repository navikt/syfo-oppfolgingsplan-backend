package no.nav.syfo.plugins

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.DevelopmentEnvironment
import no.nav.syfo.application.Environment
import no.nav.syfo.application.NaisEnvironment
import no.nav.syfo.application.database.Database
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.isDev
import no.nav.syfo.dinesykmeldte.DineSykmeldteHttpClient
import no.nav.syfo.dinesykmeldte.DineSykmeldteService
import no.nav.syfo.texas.client.TexasHttpClient
import org.koin.core.scope.Scope
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger


fun Application.configureDependencies() {
    install(Koin) {
        slf4jLogger()

        modules(
            applicationStateModule(),
            environmentModule(isDev()),
            httpClient(),
            databaseModule(),
            servicesModule(),
        )
    }
}

private fun applicationStateModule() = module { single { ApplicationState() } }

private fun environmentModule(isDev: Boolean) = module {
    single {
        if (isDev) DevelopmentEnvironment()
        else NaisEnvironment()
    }
}

private fun httpClient() = module {
    single {
        HttpClient(Apache) {
            expectSuccess = true
            install(ContentNegotiation) {
                json()
            }
            install(HttpRequestRetry) {
                retryOnExceptionIf(2) { _, cause ->
                    cause !is ClientRequestException
                }
                constantDelay(500L)
            }
        }
    }
}

private fun databaseModule() = module { single<DatabaseInterface> { Database(get()) } }

private fun servicesModule() = module {
    single { DineSykmeldteService(DineSykmeldteHttpClient(get(), env().dineSykmeldteBaseUrl)) }
    single { TexasHttpClient(env().texas) }
}

private fun Scope.env() = get<Environment>()

