package no.nav.syfo.application.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

fun defaultHttpClientClient() = HttpClient(Apache) {
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