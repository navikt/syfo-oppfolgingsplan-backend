package no.nav.syfo.oppfolgingsplan.db

import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import no.nav.syfo.application.database.dbQuery
import no.nav.syfo.oppfolgingsplan.db.OppfolgingsplanDAO.OppfolgingsplanTable
import no.nav.syfo.oppfolgingsplan.domain.Oppfolgingsplan
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID


class OppfolgingsplanDAO {

    object OppfolgingsplanTable : Table("oppfolgingsplan") {
        val uuid = uuid("uuid").autoGenerate()
        val sykmeldtFnr = varchar("sykemeldt_fnr", 11)
        val narmesteLederId = varchar("narmeste_leder_id", 150)
        val narmesteLederFnr = varchar("narmeste_leder_fnr", 11)
        val orgnummer = varchar("orgnummer", 9)
        val content = jsonb<String>("content", Json {
            prettyPrint = false
            explicitNulls = false
        })
        val sluttdato = date("sluttdato")
        val createdAt = timestamp("created_at")
        val shouldShareWithGP = bool("skal_deles_med_lege").default(false)
        val sharedWithGPTimestamp = timestamp("delt_med_lege_tidspunkt").nullable()
        val shouldShareWithNav = bool("skal_deles_med_veileder").default(false)
        val sharedWithNavTimestamp = timestamp("delt_med_veileder_tidspunkt").nullable()

        override val primaryKey = PrimaryKey(uuid)
    }

    suspend fun persist(narmesteLederId: String, oppfolgingsplan: Oppfolgingsplan): UUID {
        return dbQuery {
            OppfolgingsplanTable.insert {
                it[sykmeldtFnr] = oppfolgingsplan.sykmeldtFnr
                it[this.narmesteLederId] = narmesteLederId
                it[narmesteLederFnr] = oppfolgingsplan.narmesteLederFnr
                it[orgnummer] = oppfolgingsplan.orgnummer
                it[content] = oppfolgingsplan.content
                it[sluttdato] = oppfolgingsplan.sluttdato
                it[createdAt] = Clock.System.now()
                it[shouldShareWithGP] = oppfolgingsplan.shouldShareWithGP
                it[sharedWithGPTimestamp] = null
                it[shouldShareWithNav] = oppfolgingsplan.shouldShareWithNav
                it[sharedWithNavTimestamp] = null
            }[OppfolgingsplanTable.uuid]
        }
    }

    suspend fun findAll(narmesteLederId: String): List<Oppfolgingsplan> {
        return dbQuery {
            OppfolgingsplanTable.selectAll()
                .where { OppfolgingsplanTable.narmesteLederId eq narmesteLederId }
                .map { it.toOppfolgingsplan() }
        }
    }

    suspend fun findById(uuid: UUID): Oppfolgingsplan? {
        return dbQuery {
            OppfolgingsplanTable.selectAll()
                .where { OppfolgingsplanTable.uuid eq uuid }
                .mapNotNull { it.toOppfolgingsplan() }
                .singleOrNull()
        }
    }
}

fun ResultRow.toOppfolgingsplan(): Oppfolgingsplan {
    return Oppfolgingsplan(
        sykmeldtFnr = this[OppfolgingsplanTable.sykmeldtFnr],
        narmesteLederFnr = this[OppfolgingsplanTable.narmesteLederFnr],
        orgnummer = this[OppfolgingsplanTable.orgnummer],
        content = this[OppfolgingsplanTable.content],
        sluttdato = this[OppfolgingsplanTable.sluttdato],
        shouldShareWithGP = this[OppfolgingsplanTable.shouldShareWithGP],
        shouldShareWithNav = this[OppfolgingsplanTable.shouldShareWithNav]
    )
}