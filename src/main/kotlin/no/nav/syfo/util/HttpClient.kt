package no.nav.syfo.util

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson

private const val DEFAULT_TIMEOUT_MS = 30_000L
private const val DEFAULT_CONNECT_TIMEOUT_MS = 10_000L

fun httpClientDefault(httpClient: HttpClient = HttpClient(Apache5)): HttpClient {
    return httpClient.config {
        expectSuccess = true
        install(ContentNegotiation) {
            jackson {
                applyStandardConfiguration()
            }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = DEFAULT_TIMEOUT_MS
            connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MS
            socketTimeoutMillis = DEFAULT_TIMEOUT_MS
        }
        install(HttpRequestRetry) {
            retryOnExceptionIf(2) { _, cause ->
                cause !is ClientRequestException
            }
            constantDelay(500L)
        }
    }
}
