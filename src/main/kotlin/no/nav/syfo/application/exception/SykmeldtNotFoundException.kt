package no.nav.syfo.application.exception

class SykmeldtNotFoundException(
    message: String = "Could not find sykmeldt",
    cause: Throwable? = null,
) : ApiErrorException.NotFound(errorMessage = message, cause = cause, type = ErrorType.SYKMELDT_NOT_FOUND)
