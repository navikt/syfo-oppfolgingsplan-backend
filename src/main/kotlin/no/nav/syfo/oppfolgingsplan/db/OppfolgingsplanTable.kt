package no.nav.syfo.oppfolgingsplan.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

internal object OppfolgingsplanTable : Table("oppfolgingsplan") {
    val uuid = javaUUID("uuid").databaseGenerated()
    val sykmeldtFnr = text("sykmeldt_fnr")
    val sykmeldtFullName = text("sykmeldt_full_name")
    val narmesteLederId = text("narmeste_leder_id")
    val narmesteLederFnr = text("narmeste_leder_fnr")
    val organisasjonsnummer = text("organisasjonsnummer")
    val organisasjonsnavn = text("organisasjonsnavn").nullable()
    val stillingstittel = text("stillingstittel").nullable()
    val stillingsprosent = decimal("stillingsprosent", 5, 2).nullable()
    val content = jsonb<String>("content", { it }, { it })
    val evalueringsdato = date("evalueringsdato")
    val evalueringPaaminnelse = bool("evaluering_paaminnelse")
    val evalueringPaaminnelseOutboxAt = timestampWithTimeZone("evaluering_paaminnelse_outbox_at").nullable()
    val skalDelesMedLege = bool("skal_deles_med_lege")
    val skalDelesMedVeileder = bool("skal_deles_med_veileder")
    val utkastCreatedAt = timestampWithTimeZone("utkast_created_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val eventId = javaUUID("event_id").nullable()

    override val primaryKey = PrimaryKey(uuid)
}

internal object OppfolgingsplanUtkastTable : Table("oppfolgingsplan_utkast") {
    val uuid = javaUUID("uuid").databaseGenerated()
    val narmesteLederId = text("narmeste_leder_id")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(uuid)
}

internal object SykmeldingsperiodeTable : Table("sykmeldingsperiode") {
    val id = javaUUID("id").databaseGenerated()
    val sykmeldtFnr = text("sykmeldt_fnr")
    val organisasjonsnummer = text("organisasjonsnummer")
    val fom = date("fom")
    val tom = date("tom")
    val invalidatedAt = timestampWithTimeZone("invalidated_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
