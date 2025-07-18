package no.nav.syfo.plugins

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
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
import java.util.*
import no.nav.syfo.application.exception.ApiError
import no.nav.syfo.application.exception.ApiError.AuthenticationError
import no.nav.syfo.application.exception.ApiError.AuthorizationError
import no.nav.syfo.application.exception.ApiError.BadRequestError
import no.nav.syfo.application.exception.ApiError.IllegalArgumentError
import no.nav.syfo.application.exception.ApiError.InternalServerError
import no.nav.syfo.application.exception.ApiError.NotFoundError
import no.nav.syfo.application.exception.ForbiddenException
import no.nav.syfo.application.exception.InternalServerErrorException
import no.nav.syfo.application.exception.UnauthorizedException

const val NAV_CALL_ID_HEADER = "Nav-Call-Id"

fun Application.installContentNegotiation() {
    install(ContentNegotiation) {
        jackson {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
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

private fun determineApiError(cause: Throwable, path: String?): ApiError {
    return when (cause) {
        is BadRequestException -> BadRequestError(cause.message ?: "Bad request", path)
        is IllegalArgumentException -> IllegalArgumentError(cause.message ?: "Illegal argument", path)
        is NotFoundException -> NotFoundError(cause.message ?: "Not found", path)
        is ForbiddenException -> AuthorizationError(cause.message ?: "Forbidden", path)
        is UnauthorizedException -> AuthenticationError(cause.message ?: "Unauthorized", path)
        is InternalServerErrorException -> InternalServerError(cause.message ?: "Unauthorized", path)
        else -> InternalServerError(cause.message ?: "Internal server error", path)
    }
}

private fun logException(call: ApplicationCall, cause: Throwable) {
    val logExceptionMessage = "Caught ${cause::class.simpleName} exception"
    call.application.log.warn(logExceptionMessage, cause)
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
