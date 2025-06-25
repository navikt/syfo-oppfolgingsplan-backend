package no.nav.syfo.oppfolgingsplan.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.oppfolgingsplan.domain.Oppfolgingsplan
import java.sql.Date
import java.sql.Types

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
            it.setObject(5, oppfolgingsplan.content, Types.OTHER)
            it.setDate(6, Date.valueOf(oppfolgingsplan.sluttdato.toString()))
            it.setBoolean(7, oppfolgingsplan.shouldShareWithGP)
            it.setBoolean(8, oppfolgingsplan.shouldShareWithNav)
            it.executeUpdate()
        }
        connection.commit()
    }
}
