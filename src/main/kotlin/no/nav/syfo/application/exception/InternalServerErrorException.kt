package no.nav.syfo.application.exception

class InternalServerErrorException(
    message: String = "Unauthorized",
) : RuntimeException(message)
