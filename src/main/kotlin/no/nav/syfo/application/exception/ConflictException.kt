package no.nav.syfo.application.exception

class ConflictException(
    message: String = "Conflict",
) : RuntimeException(message)
