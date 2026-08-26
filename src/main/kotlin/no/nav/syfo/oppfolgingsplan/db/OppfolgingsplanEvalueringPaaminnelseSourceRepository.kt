package no.nav.syfo.oppfolgingsplan.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.exposedTransaction
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.select
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

private val ZONE_OSLO: ZoneId = ZoneId.of("Europe/Oslo")

class OppfolgingsplanEvalueringPaaminnelseSourceRepository(
    private val database: DatabaseInterface,
) {
    suspend fun find(
        oppfolgingsplanUuid: UUID,
        clock: Clock = Clock.systemUTC(),
    ): OppfolgingsplanEvalueringPaaminnelseSource = database.exposedTransaction(readOnly = true) {
        val today = LocalDate.now(clock.withZone(ZONE_OSLO))
        val sourceRow = EvalueringPaaminnelseSourceOppfolgingsplanTable
            .select(
                EvalueringPaaminnelseSourceOppfolgingsplanTable.sykmeldtFnr,
                EvalueringPaaminnelseSourceOppfolgingsplanTable.sykmeldtFullName,
                EvalueringPaaminnelseSourceOppfolgingsplanTable.organisasjonsnummer,
                EvalueringPaaminnelseSourceOppfolgingsplanTable.organisasjonsnavn,
                EvalueringPaaminnelseSourceOppfolgingsplanTable.evalueringsdato,
            ).where {
                EvalueringPaaminnelseSourceOppfolgingsplanTable.uuid eq oppfolgingsplanUuid
            }.singleOrNull()
            ?: return@exposedTransaction OppfolgingsplanEvalueringPaaminnelseSource.NotFound

        val sykmeldtFnr = sourceRow[EvalueringPaaminnelseSourceOppfolgingsplanTable.sykmeldtFnr]
        val organisasjonsnummer = sourceRow[EvalueringPaaminnelseSourceOppfolgingsplanTable.organisasjonsnummer]
        val hasActiveSykmeldingsperiode = EvalueringPaaminnelseSykmeldingsperiodeTable
            .select(EvalueringPaaminnelseSykmeldingsperiodeTable.id)
            .where {
                (EvalueringPaaminnelseSykmeldingsperiodeTable.sykmeldtFnr eq sykmeldtFnr) and
                    (EvalueringPaaminnelseSykmeldingsperiodeTable.organisasjonsnummer eq organisasjonsnummer) and
                    EvalueringPaaminnelseSykmeldingsperiodeTable.invalidatedAt.isNull() and
                    (EvalueringPaaminnelseSykmeldingsperiodeTable.fom lessEq today) and
                    (EvalueringPaaminnelseSykmeldingsperiodeTable.tom greaterEq today)
            }.limit(1)
            .any()

        if (!hasActiveSykmeldingsperiode) {
            return@exposedTransaction OppfolgingsplanEvalueringPaaminnelseSource.NoLongerEligible
        }

        OppfolgingsplanEvalueringPaaminnelseSource.Eligible(
            OppfolgingsplanEvalueringPaaminnelseSourceData(
                sykmeldtFnr = sykmeldtFnr,
                sykmeldtFullName = sourceRow[EvalueringPaaminnelseSourceOppfolgingsplanTable.sykmeldtFullName],
                organisasjonsnummer = organisasjonsnummer,
                organisasjonsnavn = sourceRow[EvalueringPaaminnelseSourceOppfolgingsplanTable.organisasjonsnavn],
                evalueringsdato = sourceRow[EvalueringPaaminnelseSourceOppfolgingsplanTable.evalueringsdato],
            ),
        )
    }
}

sealed interface OppfolgingsplanEvalueringPaaminnelseSource {
    data class Eligible(
        val sourceData: OppfolgingsplanEvalueringPaaminnelseSourceData,
    ) : OppfolgingsplanEvalueringPaaminnelseSource

    data object NotFound : OppfolgingsplanEvalueringPaaminnelseSource

    data object NoLongerEligible : OppfolgingsplanEvalueringPaaminnelseSource
}

data class OppfolgingsplanEvalueringPaaminnelseSourceData(
    val sykmeldtFnr: String,
    val sykmeldtFullName: String,
    val organisasjonsnummer: String,
    val organisasjonsnavn: String?,
    val evalueringsdato: LocalDate,
) {
    override fun toString(): String = "OppfolgingsplanEvalueringPaaminnelseSourceData()"
}

private object EvalueringPaaminnelseSourceOppfolgingsplanTable : Table("oppfolgingsplan") {
    val uuid = javaUUID("uuid").databaseGenerated()
    val sykmeldtFnr = text("sykmeldt_fnr")
    val sykmeldtFullName = text("sykmeldt_full_name")
    val organisasjonsnummer = text("organisasjonsnummer")
    val organisasjonsnavn = text("organisasjonsnavn").nullable()
    val evalueringsdato = date("evalueringsdato")

    override val primaryKey = PrimaryKey(uuid)
}

private object EvalueringPaaminnelseSykmeldingsperiodeTable : Table("sykmeldingsperiode") {
    val id = javaUUID("id").databaseGenerated()
    val sykmeldtFnr = text("sykmeldt_fnr")
    val organisasjonsnummer = text("organisasjonsnummer")
    val fom = date("fom")
    val tom = date("tom")
    val invalidatedAt = timestampWithTimeZone("invalidated_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
