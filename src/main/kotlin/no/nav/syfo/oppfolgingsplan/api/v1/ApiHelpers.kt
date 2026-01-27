package no.nav.syfo.oppfolgingsplan.api.v1

import io.ktor.http.Parameters
import io.ktor.server.plugins.ParameterConversionException
import no.nav.syfo.application.exception.ApiErrorException
import java.util.*

fun Parameters.extractAndValidateUUIDParameter(): UUID {
    val uuid = get("uuid")
        ?: throw ApiErrorException.BadRequest("Missing uuid parameter")

    return try {
        UUID.fromString(uuid)
    } catch (e: IllegalArgumentException) {
        throw ParameterConversionException("uuid", "UUID", e)
    }
}
