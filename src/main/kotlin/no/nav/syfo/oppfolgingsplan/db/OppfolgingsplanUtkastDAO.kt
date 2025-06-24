package no.nav.syfo.oppfolgingsplan.db

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import no.nav.syfo.application.database.dbQuery
import no.nav.syfo.oppfolgingsplan.domain.OppfolgingsplanUtkast
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert
import java.util.UUID

data class OppfolgingsplanUtkastDTO(
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

class OppfolgingsplanUtkastDAO {
    object OppfolgingsplanUtkastTable : Table("oppfolgingsplan_utkast") {
        val uuid = uuid("uuid").autoGenerate()
        val sykmeldtFnr = varchar("sykemeldt_fnr", 11)
        val narmesteLederId = varchar("narmeste_leder_id", 150).uniqueIndex()
        val narmesteLederFnr = varchar("narmeste_leder_fnr", 11)
        val orgnummer = varchar("orgnummer", 9)
        val content = jsonb<String>("content", Json {
            prettyPrint = false
            explicitNulls = false
        }).nullable()
        val sluttdato = date("sluttdato").nullable()
        val createdAt = timestamp("created_at")
        val updatedAt = timestamp("updated_at")

        override val primaryKey = PrimaryKey(uuid)
    }

    suspend fun upsert(narmesteLederId: String, utkast: OppfolgingsplanUtkast): UUID {
        return dbQuery {
            OppfolgingsplanUtkastTable.upsert {
                it[sykmeldtFnr] = utkast.sykmeldtFnr
                it[this.narmesteLederId] = narmesteLederId
                it[narmesteLederFnr] = utkast.narmesteLederFnr
                it[orgnummer] = utkast.orgnummer
                it[content] = utkast.content
                it[sluttdato] = utkast.sluttdato
                it[createdAt] = Clock.System.now()
                it[updatedAt] = Clock.System.now()
            }[OppfolgingsplanUtkastTable.uuid]
        }
    }

    suspend fun findBy(narmesteLederId: String): OppfolgingsplanUtkastDTO? {
        return dbQuery {
            OppfolgingsplanUtkastTable.selectAll()
                .where { OppfolgingsplanUtkastTable.narmesteLederId eq narmesteLederId }
                .map { it.toOppfolgingsplanUtkast() }
                .singleOrNull()
        }
    }

    suspend fun deleteBy(narmesteLederId: String) {
        dbQuery {
            OppfolgingsplanUtkastTable.deleteWhere {
                OppfolgingsplanUtkastTable.narmesteLederId eq narmesteLederId
            }
        }
    }

    fun ResultRow.toOppfolgingsplanUtkast(): OppfolgingsplanUtkastDTO {
        return OppfolgingsplanUtkastDTO(
            uuid = this[OppfolgingsplanUtkastTable.uuid],
            sykmeldtFnr = this[OppfolgingsplanUtkastTable.sykmeldtFnr],
            narmesteLederId = this[OppfolgingsplanUtkastTable.narmesteLederId],
            narmesteLederFnr = this[OppfolgingsplanUtkastTable.narmesteLederFnr],
            orgnummer = this[OppfolgingsplanUtkastTable.orgnummer],
            content = this[OppfolgingsplanUtkastTable.content],
            sluttdato = this[OppfolgingsplanUtkastTable.sluttdato],
            createdAt = this[OppfolgingsplanUtkastTable.createdAt],
            updatedAt = this[OppfolgingsplanUtkastTable.updatedAt],
        )
    }
}