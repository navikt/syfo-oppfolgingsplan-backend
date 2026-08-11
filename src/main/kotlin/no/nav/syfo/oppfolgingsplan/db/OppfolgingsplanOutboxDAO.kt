package no.nav.syfo.oppfolgingsplan.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.exposedTransaction
import no.nav.syfo.application.outbox.db.insertOutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.dinesykmeldte.client.getOrganizationName
import no.nav.syfo.oppfolgingsplan.dto.CreateOppfolgingsplanRequest
import no.nav.syfo.oppfolgingsplan.dto.formsnapshot.toJsonString
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteReturning
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

suspend fun DatabaseInterface.persistOppfolgingsplanAndDeleteUtkast(
    narmesteLederFnr: String,
    sykmeldt: Sykmeldt,
    createOppfolgingsplanRequest: CreateOppfolgingsplanRequest,
    stillingstittel: String?,
    stillingsprosent: BigDecimal?,
): UUID = exposedTransaction {
    val utkastCreatedAt = OppfolgingsplanUtkastOutboxTable
        .deleteReturning(listOf(OppfolgingsplanUtkastOutboxTable.createdAt)) {
            OppfolgingsplanUtkastOutboxTable.narmesteLederId eq sykmeldt.narmestelederId
        }.singleOrNull()
        ?.get(OppfolgingsplanUtkastOutboxTable.createdAt)

    val oppfolgingsplanUuid = UUID.randomUUID()
    val eventId = UUID.randomUUID()
    val createdAt = OffsetDateTime.now(ZoneOffset.UTC)

    OppfolgingsplanOutboxTable.insert {
        it[uuid] = oppfolgingsplanUuid
        it[sykmeldtFnr] = sykmeldt.fnr
        it[sykmeldtFullName] = sykmeldt.navn
        it[narmesteLederId] = sykmeldt.narmestelederId
        it[OppfolgingsplanOutboxTable.narmesteLederFnr] = narmesteLederFnr
        it[organisasjonsnummer] = sykmeldt.orgnummer
        it[organisasjonsnavn] = sykmeldt.getOrganizationName()
        it[OppfolgingsplanOutboxTable.stillingstittel] = stillingstittel
        it[OppfolgingsplanOutboxTable.stillingsprosent] = stillingsprosent
        it[content] = createOppfolgingsplanRequest.content.toJsonString()
        it[evalueringsdato] = createOppfolgingsplanRequest.evalueringsdato
        it[evalueringPaaminnelse] = createOppfolgingsplanRequest.evalueringPaaminnelse
        it[skalDelesMedLege] = false
        it[skalDelesMedVeileder] = false
        it[OppfolgingsplanOutboxTable.utkastCreatedAt] = utkastCreatedAt
        it[OppfolgingsplanOutboxTable.createdAt] = createdAt
        it[OppfolgingsplanOutboxTable.eventId] = eventId
    }
    insertOutboxMessage(
        uuid = eventId,
        messageType = OutboxMessageType.OPPFOLGINGSPLAN_OPPRETTET,
        dedupKey = oppfolgingsplanUuid.toString(),
        externalRef = oppfolgingsplanUuid.toString(),
        payload = "{}",
        scheduledAt = createdAt.toInstant(),
    )
    oppfolgingsplanUuid
}

fun JdbcTransaction.findOppfolgingsplanVarselRecipient(
    oppfolgingsplanUuid: UUID,
): OppfolgingsplanVarselRecipient? = OppfolgingsplanOutboxTable
    .selectAll()
    .where {
        (OppfolgingsplanOutboxTable.uuid eq oppfolgingsplanUuid) and
            OppfolgingsplanOutboxTable.skjultFra.isNull() and
            OppfolgingsplanOutboxTable.feilregistrert.isNull()
    }.singleOrNull()
    ?.let { OppfolgingsplanVarselRecipient(it[OppfolgingsplanOutboxTable.sykmeldtFnr]) }

class OppfolgingsplanVarselRecipient(
    val sykmeldtFnr: String,
) {
    override fun toString(): String = "OppfolgingsplanVarselRecipient()"
}
