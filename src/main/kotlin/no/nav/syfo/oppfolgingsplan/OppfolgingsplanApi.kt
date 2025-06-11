package no.nav.syfo.oppfolgingsplan

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.syfo.texas.TexasAuthPlugin
import no.nav.syfo.texas.TexasHttpClient

fun Routing.registerOppfolgingsplanApi(texasHttpClient: TexasHttpClient) {
    route("api/v1") {
        install(TexasAuthPlugin) {
            client = texasHttpClient
        }
        get("oppfolgingsplaner") {
            call.respond(HttpStatusCode.OK)
        }
    }
}