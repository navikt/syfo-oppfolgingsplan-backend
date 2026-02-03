package no.nav.syfo.application.exception

class LegeNotFoundException(
    message: String = "Could not find lege",
    cause: Throwable? = null,
) : ApiErrorException.NotFound(errorMessage = message, cause = cause, type = ErrorType.LEGE_NOT_FOUND)
