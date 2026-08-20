package no.nav.syfo.oppfolgingsplan.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

internal object UnntaksvurderingTable : Table("unntaksvurdering") {
    val uuid = javaUUID("uuid").databaseGenerated()
    val sykmeldtFnr = text("sykmeldt_fnr")
    val organisasjonsnummer = text("organisasjonsnummer")
    val organisasjonsnavn = text("organisasjonsnavn").nullable()
    val narmesteLederFnr = text("narmeste_leder_fnr")
    val narmesteLederFullName = text("narmeste_leder_full_name").nullable()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val skjultFra = timestampWithTimeZone("skjult_fra").nullable()

    override val primaryKey = PrimaryKey(uuid)

    init {
        index(
            customIndexName = "idx_unntaksvurdering_lookup",
            columns = arrayOf(sykmeldtFnr, organisasjonsnummer),
        )
        index(
            customIndexName = "idx_unntaksvurdering_skjult_fra_null",
            columns = arrayOf(uuid),
            filterCondition = { skjultFra.isNull() },
        )
    }
}
