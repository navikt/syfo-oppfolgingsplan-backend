package no.nav.syfo.oppfolgingsplan.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.outbox.db.cancelReadyOutboxMessages
import no.nav.syfo.application.outbox.db.enqueueOutboxMessage
import no.nav.syfo.application.outbox.domain.NewOutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxCancellationReason
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedOpprettOppfolgingsplanPaaminnelse
import no.nav.syfo.oppfolgingsplan.outbox.OppfolgingsplanOutboxMessageType
import no.nav.syfo.util.configuredJacksonMapper
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

data class OpprettOppfolgingsplanPaaminnelseOutboxPayload(
    val sykmeldingsperiodeId: UUID,
    val bestillingId: UUID? = null,
)

fun DatabaseInterface.upsertOpprettOppfolgingsplanPaaminnelse(
    sykmeldt: Sykmeldt,
    bestilt: Boolean,
    sykmeldingsperiodeId: UUID,
): PersistedOpprettOppfolgingsplanPaaminnelse = connection.use { connection ->
    upsertOpprettOppfolgingsplanPaaminnelse(connection, sykmeldt, bestilt, sykmeldingsperiodeId)
        .also { connection.commit() }
}

fun DatabaseInterface.upsertOpprettOppfolgingsplanPaaminnelseAndEnqueue(
    sykmeldt: Sykmeldt,
    sykmeldingsperiodeId: UUID,
    availableAt: Instant,
): Unit = connection.use { connection ->
    val opprettOppfolgingsplanPaaminnelse = activateOpprettOppfolgingsplanPaaminnelse(
        connection = connection,
        sykmeldt = sykmeldt,
        sykmeldingsperiodeId = sykmeldingsperiodeId,
    )
    if (opprettOppfolgingsplanPaaminnelse == null) {
        connection.commit()
        return
    }
    connection.enqueueOutboxMessage(
        NewOutboxMessage(
            messageType = OppfolgingsplanOutboxMessageType.PAAMINNELSE_ARBEIDSGIVER,
            dedupKey = "${opprettOppfolgingsplanPaaminnelse.uuid}:$sykmeldingsperiodeId:${UUID.randomUUID()}",
            externalRef = opprettOppfolgingsplanPaaminnelse.uuid.toString(),
            payload = configuredJacksonMapper.writeValueAsString(
                OpprettOppfolgingsplanPaaminnelseOutboxPayload(
                    sykmeldingsperiodeId = sykmeldingsperiodeId,
                    bestillingId = opprettOppfolgingsplanPaaminnelse.bestillingId,
                ),
            ),
            availableAt = availableAt,
        ),
    )
    connection.enqueueOutboxMessage(
        NewOutboxMessage(
            messageType = OppfolgingsplanOutboxMessageType.PAAMINNELSE_DINE_SYKMELDTE,
            dedupKey = "${opprettOppfolgingsplanPaaminnelse.uuid}:$sykmeldingsperiodeId:${UUID.randomUUID()}",
            externalRef = opprettOppfolgingsplanPaaminnelse.uuid.toString(),
            payload = configuredJacksonMapper.writeValueAsString(
                OpprettOppfolgingsplanPaaminnelseOutboxPayload(
                    sykmeldingsperiodeId = sykmeldingsperiodeId,
                    bestillingId = opprettOppfolgingsplanPaaminnelse.bestillingId,
                ),
            ),
            availableAt = availableAt,
        ),
    )
    connection.commit()
}

fun DatabaseInterface.deactivateOpprettOppfolgingsplanPaaminnelseAndCancelOutbox(
    sykmeldt: Sykmeldt,
    sykmeldingsperiodeId: UUID,
    completedAt: Instant,
): Unit = connection.use { connection ->
    val opprettOppfolgingsplanPaaminnelse = upsertOpprettOppfolgingsplanPaaminnelse(
        connection = connection,
        sykmeldt = sykmeldt,
        bestilt = false,
        sykmeldingsperiodeId = sykmeldingsperiodeId,
    )
    connection.cancelReadyOutboxMessages(
        messageType = OppfolgingsplanOutboxMessageType.PAAMINNELSE_ARBEIDSGIVER,
        externalRef = opprettOppfolgingsplanPaaminnelse.uuid.toString(),
        reason = OutboxCancellationReason.NO_LONGER_REQUESTED,
        completedAt = completedAt,
    )
    connection.cancelReadyOutboxMessages(
        messageType = OppfolgingsplanOutboxMessageType.PAAMINNELSE_DINE_SYKMELDTE,
        externalRef = opprettOppfolgingsplanPaaminnelse.uuid.toString(),
        reason = OutboxCancellationReason.NO_LONGER_REQUESTED,
        completedAt = completedAt,
    )
    connection.commit()
}

private fun activateOpprettOppfolgingsplanPaaminnelse(
    connection: java.sql.Connection,
    sykmeldt: Sykmeldt,
    sykmeldingsperiodeId: UUID,
): PersistedOpprettOppfolgingsplanPaaminnelse? {
    val statement =
        """
        INSERT INTO paaminnelse (
            organisasjonsnummer,
            sykmeldt_fnr,
            bestilt,
            created_at,
            updated_at,
            sykmeldingsperiode_id
        ) VALUES (?, ?, TRUE, NOW(), NOW(), ?)
        ON CONFLICT (sykmeldt_fnr, organisasjonsnummer) DO UPDATE SET
            bestilt = TRUE,
            sykmeldingsperiode_id = EXCLUDED.sykmeldingsperiode_id,
            bestilling_id = gen_random_uuid(),
            updated_at = NOW()
        WHERE NOT paaminnelse.bestilt
           OR paaminnelse.sykmeldingsperiode_id <> EXCLUDED.sykmeldingsperiode_id
        RETURNING *
        """.trimIndent()

    connection.prepareStatement(statement).use { preparedStatement ->
        preparedStatement.setString(1, sykmeldt.orgnummer)
        preparedStatement.setString(2, sykmeldt.fnr)
        preparedStatement.setObject(3, sykmeldingsperiodeId)

        val resultSet = preparedStatement.executeQuery()
        return if (resultSet.next()) resultSet.toPersistedOpprettOppfolgingsplanPaaminnelse() else null
    }
}

private fun upsertOpprettOppfolgingsplanPaaminnelse(
    connection: java.sql.Connection,
    sykmeldt: Sykmeldt,
    bestilt: Boolean,
    sykmeldingsperiodeId: UUID,
): PersistedOpprettOppfolgingsplanPaaminnelse {
    val statement =
        """
        INSERT INTO paaminnelse (
            organisasjonsnummer,
            sykmeldt_fnr,
            bestilt,
            created_at,
            updated_at,
            sykmeldingsperiode_id
        ) VALUES (?, ?, ?, NOW(), NOW(), ?)
        ON CONFLICT (sykmeldt_fnr, organisasjonsnummer) DO UPDATE SET
            bestilt = EXCLUDED.bestilt,
            sykmeldingsperiode_id = EXCLUDED.sykmeldingsperiode_id,
            updated_at = NOW()
        RETURNING *
        """.trimIndent()

    var idx = 0
    connection.prepareStatement(statement).use { preparedStatement ->
        preparedStatement.setString(++idx, sykmeldt.orgnummer)
        preparedStatement.setString(++idx, sykmeldt.fnr)
        preparedStatement.setBoolean(++idx, bestilt)
        preparedStatement.setObject(++idx, sykmeldingsperiodeId)

        val resultSet = preparedStatement.executeQuery()
        check(resultSet.next()) { "upsertOpprettOppfolgingsplanPaaminnelse returned no row" }
        return resultSet.toPersistedOpprettOppfolgingsplanPaaminnelse()
    }
}

fun DatabaseInterface.findOpprettOppfolgingsplanPaaminnelseBy(
    sykmeldtFnr: String,
    organisasjonsnummer: String,
): PersistedOpprettOppfolgingsplanPaaminnelse? {
    val statement =
        """
        SELECT *
        FROM paaminnelse
        WHERE sykmeldt_fnr = ?
          AND organisasjonsnummer = ?
        """.trimIndent()

    connection.use { connection ->
        var idx = 0
        connection.prepareStatement(statement).use { preparedStatement ->
            preparedStatement.setString(++idx, sykmeldtFnr)
            preparedStatement.setString(++idx, organisasjonsnummer)
            val resultSet = preparedStatement.executeQuery()

            return if (resultSet.next()) {
                resultSet.toPersistedOpprettOppfolgingsplanPaaminnelse()
            } else {
                null
            }
        }
    }
}

fun DatabaseInterface.findOpprettOppfolgingsplanPaaminnelseBy(
    uuid: UUID,
): PersistedOpprettOppfolgingsplanPaaminnelse? {
    val statement = "SELECT * FROM paaminnelse WHERE uuid = ?"

    connection.use { connection ->
        connection.prepareStatement(statement).use { preparedStatement ->
            preparedStatement.setObject(1, uuid)
            val resultSet = preparedStatement.executeQuery()

            return if (resultSet.next()) {
                resultSet.toPersistedOpprettOppfolgingsplanPaaminnelse()
            } else {
                null
            }
        }
    }
}

private fun ResultSet.toPersistedOpprettOppfolgingsplanPaaminnelse(): PersistedOpprettOppfolgingsplanPaaminnelse = PersistedOpprettOppfolgingsplanPaaminnelse(
    uuid = getObject("uuid", UUID::class.java),
    organisasjonsnummer = getString("organisasjonsnummer"),
    sykmeldtFnr = getString("sykmeldt_fnr"),
    bestilt = getBoolean("bestilt"),
    bestillingId = getObject("bestilling_id", UUID::class.java),
    sykmeldingsperiodeId = getObject("sykmeldingsperiode_id", UUID::class.java),
    createdAt = getTimestamp("created_at").toInstant(),
    updatedAt = getTimestamp("updated_at").toInstant(),
)
