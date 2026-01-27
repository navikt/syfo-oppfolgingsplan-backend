package no.nav.syfo.application.exception

import io.ktor.http.HttpStatusCode

sealed class ApiErrorException(
    message: String,
    val type: ErrorType,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    abstract fun toApiError(path: String): ApiError

    open class BadRequest(
        val errorMessage: String = "Bad request",
        cause: Throwable? = null,
        type: ErrorType = ErrorType.BAD_REQUEST,
    ) : ApiErrorException(errorMessage, type, cause) {
        override fun toApiError(path: String) = ApiError(
            status = HttpStatusCode.BadRequest,
            type = type,
            message = errorMessage,
            path = path,
        )
    }

    open class Unauthorized(
        val errorMessage: String = "Unauthorized",
        cause: Throwable? = null,
        type: ErrorType = ErrorType.AUTHENTICATION_ERROR,
    ) : ApiErrorException(errorMessage, type, cause) {
        override fun toApiError(path: String) = ApiError(
            status = HttpStatusCode.Unauthorized,
            type = type,
            message = errorMessage,
            path = path,
        )
    }

    open class Forbidden(
        val errorMessage: String = "Forbidden",
        cause: Throwable? = null,
        type: ErrorType = ErrorType.AUTHORIZATION_ERROR,
    ) : ApiErrorException(errorMessage, type, cause) {
        override fun toApiError(path: String) = ApiError(
            status = HttpStatusCode.Forbidden,
            type = type,
            message = errorMessage,
            path = path,
        )
    }

    open class NotFound(
        val errorMessage: String = "Not found",
        cause: Throwable? = null,
        type: ErrorType = ErrorType.NOT_FOUND,
    ) : ApiErrorException(errorMessage, type, cause) {
        override fun toApiError(path: String) = ApiError(
            status = HttpStatusCode.NotFound,
            type = type,
            message = errorMessage,
            path = path,
        )
    }

    open class Conflict(
        val errorMessage: String = "Conflict",
        cause: Throwable? = null,
        type: ErrorType = ErrorType.CONFLICT,
    ) : ApiErrorException(errorMessage, type, cause) {
        override fun toApiError(path: String) = ApiError(
            status = HttpStatusCode.Conflict,
            type = type,
            message = errorMessage,
            path = path,
        )
    }

    open class InternalServerError(
        val errorMessage: String = "Internal server error",
        cause: Throwable? = null,
        type: ErrorType = ErrorType.INTERNAL_SERVER_ERROR,
    ) : ApiErrorException(errorMessage, type, cause) {
        override fun toApiError(path: String) = ApiError(
            status = HttpStatusCode.InternalServerError,
            type = type,
            message = errorMessage,
            path = path,
        )
    }
}
