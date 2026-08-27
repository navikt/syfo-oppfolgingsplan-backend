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

class EvalueringspaaminnelseSourceRepository(
    private val database: DatabaseInterface,
) {
    suspend fun findSource(
        oppfolgingsplanUuid: UUID,
        clock: Clock = Clock.systemUTC(),
    ): EvalueringspaaminnelseSource = database.exposedTransaction(readOnly = true) {
        val today = LocalDate.now(clock.withZone(ZONE_OSLO))
        val sourceRow = OppfolgingsplanTable
            .select(
                OppfolgingsplanTable.sykmeldtFnr,
                OppfolgingsplanTable.organisasjonsnummer,
                OppfolgingsplanTable.skjultFra,
                OppfolgingsplanTable.feilregistrert,
            ).where {
                OppfolgingsplanTable.uuid eq oppfolgingsplanUuid
            }.singleOrNull()
            ?: return@exposedTransaction EvalueringspaaminnelseSource.NotFound

        if (sourceRow[OppfolgingsplanTable.skjultFra] != null || sourceRow[OppfolgingsplanTable.feilregistrert] != null) {
            return@exposedTransaction EvalueringspaaminnelseSource.NoLongerEligible
        }

        val sykmeldtFnr = sourceRow[OppfolgingsplanTable.sykmeldtFnr]
        val organisasjonsnummer = sourceRow[OppfolgingsplanTable.organisasjonsnummer]
        val hasActiveSykmeldingsperiode = EvalueringspaaminnelseSourceSykmeldingsperiodeTable
            .select(EvalueringspaaminnelseSourceSykmeldingsperiodeTable.id)
            .where {
                (EvalueringspaaminnelseSourceSykmeldingsperiodeTable.sykmeldtFnr eq sykmeldtFnr) and
                    (
                        EvalueringspaaminnelseSourceSykmeldingsperiodeTable.organisasjonsnummer eq
                            organisasjonsnummer
                        ) and
                    EvalueringspaaminnelseSourceSykmeldingsperiodeTable.invalidatedAt.isNull() and
                    (EvalueringspaaminnelseSourceSykmeldingsperiodeTable.fom lessEq today) and
                    (EvalueringspaaminnelseSourceSykmeldingsperiodeTable.tom greaterEq today)
            }.limit(1)
            .any()

        if (!hasActiveSykmeldingsperiode) {
            return@exposedTransaction EvalueringspaaminnelseSource.NoLongerEligible
        }

        EvalueringspaaminnelseSource.Eligible(
            EvalueringspaaminnelseSourceData(
                sykmeldtFnr = sykmeldtFnr,
                organisasjonsnummer = organisasjonsnummer,
            ),
        )
    }
}

sealed interface EvalueringspaaminnelseSource {
    data class Eligible(
        val data: EvalueringspaaminnelseSourceData,
    ) : EvalueringspaaminnelseSource

    data object NotFound : EvalueringspaaminnelseSource

    data object NoLongerEligible : EvalueringspaaminnelseSource
}

data class EvalueringspaaminnelseSourceData(
    val sykmeldtFnr: String,
    val organisasjonsnummer: String,
) {
    override fun toString(): String = "EvalueringspaaminnelseSourceData()"
}

private object EvalueringspaaminnelseSourceSykmeldingsperiodeTable : Table("sykmeldingsperiode") {
    val id = javaUUID("id").databaseGenerated()
    val sykmeldtFnr = text("sykmeldt_fnr")
    val organisasjonsnummer = text("organisasjonsnummer")
    val fom = date("fom")
    val tom = date("tom")
    val invalidatedAt = timestampWithTimeZone("invalidated_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
