package no.nav.syfo.application.exception

class PlanNotFoundException(
    message: String = "Could not find plan",
) : RuntimeException(message)
