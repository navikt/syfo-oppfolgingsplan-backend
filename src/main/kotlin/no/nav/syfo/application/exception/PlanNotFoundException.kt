package no.nav.syfo.application.exception

class PlanNotFoundException(
    message: String = "Could not find plan",
    cause: Throwable? = null,
) : ApiErrorException.NotFound(errorMessage = message, cause = cause, type = ErrorType.PLAN_NOT_FOUND)
