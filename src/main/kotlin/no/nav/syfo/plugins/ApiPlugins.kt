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
import no.nav.syfo.application.exception.ApiError.AuthenticationError
import no.nav.syfo.application.exception.ApiError.AuthorizationError
import no.nav.syfo.application.exception.ApiError.BadRequestError
import no.nav.syfo.application.exception.ApiError.IllegalArgumentError
import no.nav.syfo.application.exception.ApiError.InternalServerError
import no.nav.syfo.application.exception.ApiError.NotFoundError
import no.nav.syfo.application.exception.ConflictException
import no.nav.syfo.application.exception.ForbiddenException
import no.nav.syfo.application.exception.InternalServerErrorException
import no.nav.syfo.application.exception.LegeNotFoundException
import no.nav.syfo.application.exception.PlanNotFoundException
import no.nav.syfo.application.exception.SykmeldtNotFoundException
import no.nav.syfo.application.exception.UnauthorizedException
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

private fun determineApiError(cause: Throwable, path: String?): ApiError {
    return when (cause) {
        is BadRequestException -> BadRequestError(cause.message ?: "Bad request", path)
        is ConflictException -> ApiError.ConflictRequestError(cause.message ?: "Conflict", path)
        is IllegalArgumentException -> IllegalArgumentError(cause.message ?: "Illegal argument", path)
        is NotFoundException -> NotFoundError(cause.message ?: "Not found", path)
        is ForbiddenException -> AuthorizationError(cause.message ?: "Forbidden", path)
        is UnauthorizedException -> AuthenticationError(cause.message ?: "Unauthorized", path)
        is InternalServerErrorException -> InternalServerError(cause.message ?: "Internal server error", path)
        is LegeNotFoundException -> ApiError.LegeNotFoundError(cause.message ?: "Lege not found", path)
        is PlanNotFoundException -> ApiError.PlanNotFoundError(cause.message ?: "Plan not found", path)
        is SykmeldtNotFoundException -> ApiError.SykmeldtNotFoundError(cause.message ?: "Sykmeldt not found", path)
        else -> InternalServerError(cause.message ?: "Internal server error", path)
    }
}

private fun logException(call: ApplicationCall, cause: Throwable) {
    val logExceptionMessage = "Caught ${cause::class.simpleName} exception"
    call.application.log.error(logExceptionMessage, cause)
}


fun Application.installStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logException(call, cause)
            val apiError = determineApiError(cause, call.request.path())
            call.respond(apiError.status, apiError)
        }
        status(HttpStatusCode.Forbidden) { call, status ->
            val path = call.request.path()
            val apiError = AuthorizationError(status.description, path)
            call.respond(apiError.status, apiError)
        }
        status(HttpStatusCode.Unauthorized) { call, status ->
            val path = call.request.path()
            val apiError = AuthenticationError(status.description, path)
            call.respond(apiError.status, apiError)
        }
    }
}
