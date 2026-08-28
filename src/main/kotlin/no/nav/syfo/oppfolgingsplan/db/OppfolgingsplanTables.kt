package no.nav.syfo.oppfolgingsplan.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

internal object OppfolgingsplanTable : Table("oppfolgingsplan") {
    val uuid = javaUUID("uuid").databaseGenerated()
    val sykmeldtFnr = varchar("sykmeldt_fnr", 11)
    val narmesteLederId = varchar("narmeste_leder_id", 150)
    val narmesteLederFnr = varchar("narmeste_leder_fnr", 11)
    val content = jsonb<String>("content", { it }, { it })
    val evalueringsdato = date("evalueringsdato")
    val createdAt = timestampWithTimeZone("created_at")
    val skalDelesMedLege = bool("skal_deles_med_lege")
    val deltMedLegeTidspunkt = timestampWithTimeZone("delt_med_lege_tidspunkt").nullable()
    val skalDelesMedVeileder = bool("skal_deles_med_veileder")
    val deltMedVeilederTidspunkt = timestampWithTimeZone("delt_med_veileder_tidspunkt").nullable()
    val sykmeldtFullName = varchar("sykmeldt_full_name", 255)
    val organisasjonsnavn = varchar("organisasjonsnavn", 255).nullable()
    val narmesteLederFullName = varchar("narmeste_leder_full_name", 255).nullable()
    val organisasjonsnummer = varchar("organisasjonsnummer", 9)
    val sendtTilDokumentportenTidspunkt = timestampWithTimeZone("sendt_til_dokumentporten_tidspunkt").nullable()
    val utkastCreatedAt = timestampWithTimeZone("utkast_created_at").nullable()
    val journalpostId = varchar("journalpost_id", 36).nullable()
    val stillingstittel = text("stillingstittel").nullable()
    val stillingsprosent = decimal("stillingsprosent", 5, 2).nullable()
    val skjultFra = timestampWithTimeZone("skjult_fra").nullable()
    val feilregistrert = timestampWithTimeZone("feilregistrert").nullable()
    val feilregistrertAarsak = text("feilregistrert_aarsak").nullable()
    val evalueringPaaminnelse = bool("evaluering_paaminnelse").default(false)
    val eventId = javaUUID("event_id").nullable()
    val varselPublishedAt = timestampWithTimeZone("varsel_published_at").nullable()

    override val primaryKey = PrimaryKey(uuid)

    init {
        index("oppfolgingsplan_nl_idx", false, narmesteLederId)
        index("oppfolgingsplan_created_at_idx", false, createdAt)
        index("oppfolgingsplan_sykmeldt_fnr_idx", false, sykmeldtFnr)
        index(
            customIndexName = "idx_oppfolgingsplan_visible_lookup",
            columns = arrayOf(sykmeldtFnr, organisasjonsnummer, createdAt),
            filterCondition = { skjultFra eq null },
        )
    }
}

internal object OppfolgingsplanUtkastTable : Table("oppfolgingsplan_utkast") {
    val uuid = javaUUID("uuid").databaseGenerated()
    val sykmeldtFnr = varchar("sykmeldt_fnr", 11)
    val narmesteLederId = varchar("narmeste_leder_id", 150)
    val narmesteLederFnr = varchar("narmeste_leder_fnr", 11)
    val organisasjonsnummer = varchar("organisasjonsnummer", 9)
    val content = jsonb<String>("content", { it }, { it })
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(uuid)

    init {
        uniqueIndex("oppfolgingsplan_utkast_narmeste_leder_id_key", narmesteLederId)
        index("utkast_nl_idx", false, narmesteLederId)
        index("oppfolgingsplan_utkast_sykmeldt_fnr_idx", false, sykmeldtFnr)
        index("oppfolgingsplan_utkast_updated_at_idx", false, updatedAt)
    }
}
