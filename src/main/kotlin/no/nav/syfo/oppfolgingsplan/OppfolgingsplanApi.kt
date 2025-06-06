package no.nav.syfo.oppfolgingsplan

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.syfo.texas.TexasAuthPlugin
import no.nav.syfo.texas.TexasEnvironment

fun Routing.registerOppfolgingsplanApi(texasEnvironment: TexasEnvironment) {
    route("api/v1") {
        install(TexasAuthPlugin) {
            environment = texasEnvironment
        }
        get("oppfolgingsplaner") {
            call.respond(HttpStatusCode.OK)
        }
    }
}