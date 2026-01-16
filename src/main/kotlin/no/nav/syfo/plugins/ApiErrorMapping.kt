package no.nav.syfo.plugins

import com.fasterxml.jackson.module.kotlin.KotlinInvalidNullException
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import no.nav.syfo.application.exception.ApiError
import no.nav.syfo.application.exception.ApiErrorException
import no.nav.syfo.application.exception.ErrorType

internal fun BadRequestException.toApiError(path: String): ApiError {
    val rootCause = rootCause()

    return if (rootCause is KotlinInvalidNullException) {
        ApiErrorException.BadRequest(
            errorMessage = "Invalid request body. Missing required field: ${rootCause.propertyName}",
            type = ErrorType.BAD_REQUEST,
        ).toApiError(path)
    } else {
        ApiError(
            status = HttpStatusCode.BadRequest,
            type = ErrorType.BAD_REQUEST,
            message = message ?: "Bad request",
            path = path,
        )
    }
}

internal fun NotFoundException.toApiError(path: String): ApiError = ApiError(
    status = HttpStatusCode.NotFound,
    type = ErrorType.NOT_FOUND,
    message = message ?: "Not found",
    path = path,
)

internal fun Throwable.rootCause(): Throwable {
    var root: Throwable = this
    while (root.cause != null && root.cause != root) {
        root = root.cause!!
    }
    return root
}

