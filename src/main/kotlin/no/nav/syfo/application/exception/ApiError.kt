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
}
open class ApiError(
    val status: HttpStatusCode,
    val type: ErrorType,
    open val message: String,
    val timestamp: Instant,
    open val path: String? = null,
) {
    data class NotFoundError(override val message: String, override val path: String?) :
        ApiError(HttpStatusCode.NotFound, ErrorType.NOT_FOUND, message, Instant.now())

    data class InternalServerError(override val message: String, override val path: String?) :
        ApiError(HttpStatusCode.InternalServerError, ErrorType.INTERNAL_SERVER_ERROR, message, Instant.now())

    data class IllegalArgumentError(override val message: String, override val path: String?) :
        ApiError(HttpStatusCode.BadRequest, ErrorType.ILLEGAL_ARGUMENT, message, Instant.now())

    data class BadRequestError(override val message: String, override val path: String?) :
        ApiError(HttpStatusCode.BadRequest, ErrorType.BAD_REQUEST, message, Instant.now())

    data class AuthenticationError(override val message: String,  override val path: String?) :
        ApiError(HttpStatusCode.Unauthorized, ErrorType.AUTHENTICATION_ERROR, message, Instant.now())

    data class AuthorizationError(override val message: String,  override val path: String?) :
        ApiError(HttpStatusCode.Forbidden, ErrorType.AUTHORIZATION_ERROR, message, Instant.now())
    data class LegeNotFoundError(override val message: String, override val path: String?) :
        ApiError(HttpStatusCode.NotFound, ErrorType.LEGE_NOT_FOUND, message, Instant.now())
}
