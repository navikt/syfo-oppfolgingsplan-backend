package no.nav.syfo.application.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import no.nav.syfo.texas.TexasAuthPlugin
import no.nav.syfo.texas.TexasEnvironment

fun Routing.registerFollowUpPlanApi(texasEnvironment: TexasEnvironment) {

    get("/api/v1/followupplans") {
        install(TexasAuthPlugin) {
            environment = texasEnvironment
        }
        call.respond(HttpStatusCode.OK)
    }
}