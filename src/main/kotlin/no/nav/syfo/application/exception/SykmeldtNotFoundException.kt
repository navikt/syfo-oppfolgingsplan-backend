package no.nav.syfo.application.exception

class SykmeldtNotFoundException(
    message: String = "Could not find sykmeldt",
) : RuntimeException(message)
