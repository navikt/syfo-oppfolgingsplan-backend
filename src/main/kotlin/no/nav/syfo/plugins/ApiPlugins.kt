package no.nav.syfo.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import no.nav.syfo.application.exception.ApiError
import no.nav.syfo.application.exception.ApiErrorException
import no.nav.syfo.application.exception.ErrorType
import no.nav.syfo.util.applyStandardConfiguration
import java.util.*

const val NAV_CALL_ID_HEADER = "Nav-Call-Id"

fun Application.installContentNegotiation() {
    install(ContentNegotiation) {
        jackson {
            applyStandardConfiguration()
        }
    }
}

fun Application.installCallId() {
    install(CallId) {
        retrieve { it.request.headers[NAV_CALL_ID_HEADER] }
        generate { UUID.randomUUID().toString() }
        verify { callId: String -> callId.isNotEmpty() }
        header(NAV_CALL_ID_HEADER)
    }
}

private fun logException(call: ApplicationCall, cause: Throwable) {
    val logExceptionMessage = "Caught ${cause::class.simpleName} exception"
    call.application.log.error(logExceptionMessage, cause)
}

private fun determineApiError(cause: Throwable, path: String): ApiError {
    return when (cause) {
        is BadRequestException -> cause.toApiError(path)
        is NotFoundException -> cause.toApiError(path)
        is ApiErrorException -> cause.toApiError(path)
        is IllegalArgumentException -> ApiErrorException.BadRequest(
            errorMessage = cause.message ?: "Illegal argument",
            type = ErrorType.ILLEGAL_ARGUMENT,
            cause = cause,
        ).toApiError(path)

        else -> ApiError(
            status = HttpStatusCode.InternalServerError,
            type = ErrorType.INTERNAL_SERVER_ERROR,
            message = cause.message ?: "Internal server error",
            path = path,
        )
    }
}

fun Application.installStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logException(call, cause)
            val apiError = determineApiError(cause, call.request.path())
            call.respond(apiError.status, apiError)
        }
    }
}
