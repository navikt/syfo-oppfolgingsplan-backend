package no.nav.syfo.application.auth

import no.nav.syfo.dinesykmeldte.Sykmeldt

data class BrukerPrincipal(
    val ident: String,
    val token: String,
    var sykmeldt: Sykmeldt? = null,
)