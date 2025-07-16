package no.nav.syfo.oppfolgingsplan.api.v1

import io.ktor.http.Parameters
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ParameterConversionException
import java.util.UUID

fun Parameters.extractAndValidateUUIDParameter(): UUID {
    val uuid = get("uuid")
    if (uuid == null) {
        throw BadRequestException("Missing uuid parameter")
    }

    return try {
        UUID.fromString(uuid)
    } catch (e: IllegalArgumentException) {
        throw ParameterConversionException("uuid", "UUID", e)
    }
}
