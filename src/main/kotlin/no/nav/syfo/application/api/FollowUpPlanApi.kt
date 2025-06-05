package no.nav.syfo.application.api

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Routing.registerFollowUpPlanApi() {
    get("/followupplans") {
        call.respond(HttpStatusCode.OK)
    }
}