package no.nav.syfo.oppfolgingsplan.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.exposedTransaction
import no.nav.syfo.sykmelding.db.SykmeldingsperiodeTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.select
import java.time.LocalDate
import java.util.UUID

class EvalueringspaaminnelseSourceRepository(
    private val database: DatabaseInterface,
) {
    suspend fun findSourceFacts(
        oppfolgingsplanUuid: UUID,
        today: LocalDate,
    ): EvalueringspaaminnelseSourceFacts? = database.exposedTransaction(readOnly = true) {
        val sourceRow = OppfolgingsplanTable
            .select(
                OppfolgingsplanTable.sykmeldtFnr,
                OppfolgingsplanTable.organisasjonsnummer,
                OppfolgingsplanTable.skjultFra,
                OppfolgingsplanTable.feilregistrert,
            ).where {
                OppfolgingsplanTable.uuid eq oppfolgingsplanUuid
            }.singleOrNull()
            ?: return@exposedTransaction null

        val sykmeldtFnr = sourceRow[OppfolgingsplanTable.sykmeldtFnr]
        val organisasjonsnummer = sourceRow[OppfolgingsplanTable.organisasjonsnummer]
        val hasActiveSykmeldingsperiode = SykmeldingsperiodeTable
            .select(SykmeldingsperiodeTable.id)
            .where {
                (SykmeldingsperiodeTable.sykmeldtFnr eq sykmeldtFnr) and
                    (
                        SykmeldingsperiodeTable.organisasjonsnummer eq organisasjonsnummer
                        ) and
                    SykmeldingsperiodeTable.invalidatedAt.isNull() and
                    (SykmeldingsperiodeTable.fom lessEq today) and
                    (SykmeldingsperiodeTable.tom greaterEq today)
            }.limit(1)
            .any()

        EvalueringspaaminnelseSourceFacts(
            sykmeldtFnr = sykmeldtFnr,
            organisasjonsnummer = organisasjonsnummer,
            isHidden = sourceRow[OppfolgingsplanTable.skjultFra] != null,
            isRegisteredIncorrectly = sourceRow[OppfolgingsplanTable.feilregistrert] != null,
            hasActiveSykmeldingsperiode = hasActiveSykmeldingsperiode,
        )
    }
}

data class EvalueringspaaminnelseSourceFacts(
    val sykmeldtFnr: String,
    val organisasjonsnummer: String,
    val isHidden: Boolean,
    val isRegisteredIncorrectly: Boolean,
    val hasActiveSykmeldingsperiode: Boolean,
) {
    override fun toString(): String = "EvalueringspaaminnelseSourceFacts(" +
        "isHidden=$isHidden, " +
        "isRegisteredIncorrectly=$isRegisteredIncorrectly, " +
        "hasActiveSykmeldingsperiode=$hasActiveSykmeldingsperiode)"
}
