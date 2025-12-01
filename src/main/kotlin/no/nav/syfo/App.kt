package no.nav.syfo

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.syfo.application.api.configureRouting
import no.nav.syfo.plugins.configureDependencies
import no.nav.syfo.plugins.configureLifecycleHooks
import org.koin.ktor.ext.get
import java.util.concurrent.TimeUnit
import no.nav.syfo.plugins.configureBackgroundTasks

fun main() {
    val server = embeddedServer(
        Netty,
        port = 8080,
        module = Application::module
    )

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(10, 10, TimeUnit.SECONDS)
        }
    )

    server.start(true)
}

fun Application.module() {
    configureDependencies()
    configureBackgroundTasks()
    configureRouting()
    configureLifecycleHooks(get())
}
