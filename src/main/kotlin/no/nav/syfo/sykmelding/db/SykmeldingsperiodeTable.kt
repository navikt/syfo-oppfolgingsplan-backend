package no.nav.syfo.sykmelding.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

internal object SykmeldingsperiodeTable : Table("sykmeldingsperiode") {
    val id = javaUUID("id").databaseGenerated()
    val sykmeldtFnr = text("sykmeldt_fnr")
    val organisasjonsnummer = text("organisasjonsnummer")
    val fom = date("fom")
    val tom = date("tom")
    val invalidatedAt = timestampWithTimeZone("invalidated_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
