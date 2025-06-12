package no.nav.syfo.oppfolgingsplan.domain

import kotlinx.serialization.Serializable

@Serializable
data class Oppfolgingsplan(
    var narmestelederId: String
    // TODO: plus other fields as needed
)