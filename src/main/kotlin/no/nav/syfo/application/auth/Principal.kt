package no.nav.syfo.application.auth

import no.nav.syfo.dinesykmeldte.Sykmeldt

interface Principal {
    val ident: String
    val token: String
}

data class BrukerPrincipal(
    override val ident: String,
    override val token: String,
) : Principal

data class NarmesteLederPrincipal(
    override val ident: String,
    override val token: String,
    val sykmeldt: Sykmeldt
) : Principal