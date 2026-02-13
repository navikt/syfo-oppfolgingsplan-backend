package no.nav.syfo.application.exception

import io.ktor.http.HttpStatusCode
import java.time.Instant

enum class ErrorType {
    AUTHENTICATION_ERROR,
    AUTHORIZATION_ERROR,
    NOT_FOUND,
    INTERNAL_SERVER_ERROR,
    ILLEGAL_ARGUMENT,
    BAD_REQUEST,
    LEGE_NOT_FOUND,
    PLAN_NOT_FOUND,
    SYKMELDT_NOT_FOUND,
    CONFLICT,
}

data class ApiError(
    val status: HttpStatusCode,
    val type: ErrorType,
    val message: String,
    val path: String? = null,
    val timestamp: Instant = Instant.now(),
)
