package no.nav.syfo.plugins

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import java.util.*

const val NAV_CALL_ID_HEADER = "Nav-Call-Id"

fun Application.installContentNegotiation() {
    install(ContentNegotiation) {
        json(
            Json {
                serializersModule = SerializersModule {
                    contextual(LocalDate.serializer())
                }
            }
        )
    }
}

fun Application.installCallId() {
    install(CallId) {
        retrieve { it.request.headers[NAV_CALL_ID_HEADER] }
        generate { UUID.randomUUID().toString() }
        verify { callId: String -> callId.isNotEmpty() }
        header(NAV_CALL_ID_HEADER)
    }
}

fun Application.installStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
        }
    }
}
