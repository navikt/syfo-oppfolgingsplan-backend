package no.nav.syfo.oppfolgingsplan.db

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.oppfolgingsplan.domain.Oppfolgingsplan
import java.sql.Date
import java.sql.ResultSet
import java.sql.Types
import java.time.Instant
import java.util.UUID

data class PersistedOppfolgingsplan(
    val uuid: UUID,
    val sykmeldtFnr: String,
    val narmesteLederId: String,
    val narmesteLederFnr: String,
    val orgnummer: String,
    val content: JsonElement,
    val sluttdato: LocalDate,
    val skalDelesMedLege: Boolean,
    val skalDelesMedVeileder: Boolean,
    val deltMedLegeTidspunkt: Instant? = null,
    val deltMedVeilederTidspunkt: Instant? = null,
)

fun DatabaseInterface.persistOppfolgingsplanAndDeleteUtkast(
    narmesteLederId: String,
    oppfolgingsplan: Oppfolgingsplan
) {
    val insertStatement = """
        INSERT INTO oppfolgingsplan (
            sykemeldt_fnr,
            narmeste_leder_id,
            narmeste_leder_fnr,
            orgnummer,
            content,
            sluttdato,
            skal_deles_med_lege,
            skal_deles_med_veileder,
            created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
    """.trimIndent()

    val deleteStatement = """
        DELETE FROM oppfolgingsplan_utkast
        WHERE narmeste_leder_id = ?
    """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(deleteStatement).use {
            it.setString(1, narmesteLederId)
            it.executeUpdate()
        }
        connection.prepareStatement(insertStatement).use {
            it.setString(1, oppfolgingsplan.sykmeldtFnr)
            it.setString(2, narmesteLederId)
            it.setString(3, oppfolgingsplan.narmesteLederFnr)
            it.setString(4, oppfolgingsplan.orgnummer)
            it.setObject(5, oppfolgingsplan.content.toString(), Types.OTHER)
            it.setDate(6, Date.valueOf(oppfolgingsplan.sluttdato.toString()))
            it.setBoolean(7, oppfolgingsplan.skalDelesMedLege)
            it.setBoolean(8, oppfolgingsplan.skalDelesMedVeileder)
            it.executeUpdate()
        }
        connection.commit()
    }
}

fun DatabaseInterface.findAllByNarmesteLederId(
    narmesteLederId: String,
): List<PersistedOppfolgingsplan> {
    val statement = """
        SELECT *
        FROM oppfolgingsplan
        WHERE narmeste_leder_id = ?
    """.trimIndent()

    return connection.use { connection ->
        connection.prepareStatement(statement).use { preparedStatement ->
            preparedStatement.setString(1, narmesteLederId)
            preparedStatement.executeQuery().use { resultSet ->
                generateSequence { if (resultSet.next()) resultSet else null }
                    .map { it.mapToOppfolgingsplan() }
                    .toList()
            }
        }
    }
}

fun ResultSet.mapToOppfolgingsplan(): PersistedOppfolgingsplan {
    return PersistedOppfolgingsplan(
        uuid = UUID.fromString(this.getString("uuid")),
        sykmeldtFnr = this.getString("sykemeldt_fnr"),
        narmesteLederId = this.getString("narmeste_leder_id"),
        narmesteLederFnr = this.getString("narmeste_leder_fnr"),
        orgnummer = this.getString("orgnummer"),
        content = Json.parseToJsonElement(this.getObject("content").toString()),
        sluttdato = LocalDate.parse(this.getString("sluttdato")),
        skalDelesMedLege = this.getBoolean("skal_deles_med_lege"),
        skalDelesMedVeileder = this.getBoolean("skal_deles_med_veileder"),
        deltMedLegeTidspunkt = this.getObject("delt_med_lege_tidspunkt") as? Instant,
        deltMedVeilederTidspunkt = this.getObject("delt_med_veileder_tidspunkt") as? Instant,
    )
}
