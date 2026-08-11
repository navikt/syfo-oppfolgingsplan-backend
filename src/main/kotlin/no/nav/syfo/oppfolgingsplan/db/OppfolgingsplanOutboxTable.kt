package no.nav.syfo.oppfolgingsplan.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

/**
 * The subset of the legacy oppfolgingsplan tables used by the Exposed outbox flow.
 * The full tables are still migrated incrementally and remain available to the legacy JDBC DAOs.
 */
internal object OppfolgingsplanOutboxTable : Table("oppfolgingsplan") {
    val uuid = javaUUID("uuid")
    val sykmeldtFnr = varchar("sykmeldt_fnr", 11)
    val sykmeldtFullName = varchar("sykmeldt_full_name", 255)
    val narmesteLederId = varchar("narmeste_leder_id", 150)
    val narmesteLederFnr = varchar("narmeste_leder_fnr", 11)
    val organisasjonsnummer = varchar("organisasjonsnummer", 9)
    val organisasjonsnavn = varchar("organisasjonsnavn", 255).nullable()
    val stillingstittel = text("stillingstittel").nullable()
    val stillingsprosent = decimal("stillingsprosent", precision = 5, scale = 2).nullable()
    val content = jsonb<String>("content", { it }, { it })
    val evalueringsdato = date("evalueringsdato")
    val evalueringPaaminnelse = bool("evaluering_paaminnelse")
    val evalueringPaaminnelseOutboxAt = timestampWithTimeZone("evaluering_paaminnelse_outbox_at").nullable()
    val skalDelesMedLege = bool("skal_deles_med_lege")
    val skalDelesMedVeileder = bool("skal_deles_med_veileder")
    val utkastCreatedAt = timestampWithTimeZone("utkast_created_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val eventId = javaUUID("event_id").nullable()
    val varselPublishedAt = timestampWithTimeZone("varsel_published_at").nullable()
    val skjultFra = timestampWithTimeZone("skjult_fra").nullable()
    val feilregistrert = timestampWithTimeZone("feilregistrert").nullable()

    override val primaryKey = PrimaryKey(uuid)
}

internal object OppfolgingsplanUtkastOutboxTable : Table("oppfolgingsplan_utkast") {
    val narmesteLederId = varchar("narmeste_leder_id", 150)
    val createdAt = timestampWithTimeZone("created_at")
}
