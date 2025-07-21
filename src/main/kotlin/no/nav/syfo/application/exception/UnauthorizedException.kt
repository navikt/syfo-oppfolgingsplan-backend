package no.nav.syfo.application.exception

class UnauthorizedException(
    message: String = "Unauthorized",
) : RuntimeException(message)
