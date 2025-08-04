package no.nav.syfo.application.exception

class LegeNotFoundException(
    message: String = "Unable to determine fastlege",
) : RuntimeException(message)
