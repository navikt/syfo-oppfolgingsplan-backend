package no.nav.syfo.oppfolgingsplan.db

import kotlinx.datetime.LocalDate
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.oppfolgingsplan.domain.OppfolgingsplanUtkast
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

data class OppfolgingsplanUtkastDTO (
    val uuid: UUID,
    val sykmeldtFnr: String,
    val narmesteLederId: String,
    val narmesteLederFnr: String,
    val orgnummer: String,
    val content: String?,
    val sluttdato: LocalDate?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun DatabaseInterface.upsertOppfolgingsplanUtkast(
    narmesteLederId: String,
    oppfolgingsplanUtkast: OppfolgingsplanUtkast,
) {
    val statement =
        """
        INSERT INTO oppfolgingsplan_utkast (
            sykemeldt_fnr,
            narmeste_leder_id,
            narmeste_leder_fnr,
            orgnummer,
            content,
            sluttdato,
            created_at,
            updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
        ON CONFLICT (narmeste_leder_id) DO UPDATE SET
            sykemeldt_fnr = EXCLUDED.sykemeldt_fnr,
            narmeste_leder_fnr = EXCLUDED.narmeste_leder_fnr,
            orgnummer = EXCLUDED.orgnummer,
            content = EXCLUDED.content,
            sluttdato = EXCLUDED.sluttdato,
            updated_at = NOW()
        """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(statement).use { preparedStatement ->
            preparedStatement.setString(1, oppfolgingsplanUtkast.sykmeldtFnr)
            preparedStatement.setString(2, narmesteLederId)
            preparedStatement.setString(3, oppfolgingsplanUtkast.narmesteLederFnr)
            preparedStatement.setString(4, oppfolgingsplanUtkast.orgnummer)
            preparedStatement.setObject(5, oppfolgingsplanUtkast.content)
            preparedStatement.setObject(6, oppfolgingsplanUtkast.sluttdato)
            preparedStatement.executeUpdate()
        }
        connection.commit()
    }
}

fun DatabaseInterface.findOppfolgingsplanUtkastByNarmesteLederId(
    narmesteLederId: String,
): OppfolgingsplanUtkastDTO? {
    val statement =
        """
        SELECT *
        FROM oppfolgingsplan_utkast
        WHERE narmeste_leder_id = ?
        """.trimIndent()

    connection.use { connection ->
        connection.prepareStatement(statement).use { preparedStatement ->
            preparedStatement.setString(1, narmesteLederId)
            val resultSet = preparedStatement.executeQuery()
            return if (resultSet.next()) {
                resultSet.toOppfolgingsplanUtkastDTO()
            } else {
                null
            }
        }
    }
}

fun ResultSet.toOppfolgingsplanUtkastDTO(): OppfolgingsplanUtkastDTO {
    return OppfolgingsplanUtkastDTO(
        uuid = getObject("uuid") as UUID,
        sykmeldtFnr = getString("sykemeldt_fnr"),
        narmesteLederId = getString("narmeste_leder_id"),
        narmesteLederFnr = getString("narmeste_leder_fnr"),
        orgnummer = getString("orgnummer"),
        content = getString("content"),
        sluttdato = getObject("sluttdato") as LocalDate?,
        createdAt = getObject("created_at") as Instant,
        updatedAt = getObject("updated_at") as Instant,
    )
}
