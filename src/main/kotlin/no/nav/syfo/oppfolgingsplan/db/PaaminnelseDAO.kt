package no.nav.syfo.oppfolgingsplan.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.outbox.db.cancelReadyOutboxMessages
import no.nav.syfo.application.outbox.db.enqueueOutboxMessage
import no.nav.syfo.application.outbox.domain.NewOutboxMessage
import no.nav.syfo.application.outbox.domain.OutboxCancellationReason
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedPaaminnelse
import no.nav.syfo.oppfolgingsplan.outbox.OppfolgingsplanOutboxMessageType
import no.nav.syfo.util.configuredJacksonMapper
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

data class PaaminnelseOutboxPayload(
    val sykmeldingsperiodeId: UUID,
)

fun DatabaseInterface.upsertPaaminnelse(
    sykmeldt: Sykmeldt,
    bestilt: Boolean,
    sykmeldingsperiodeId: UUID,
): PersistedPaaminnelse = connection.use { connection ->
    upsertPaaminnelse(connection, sykmeldt, bestilt, sykmeldingsperiodeId)
        .also { connection.commit() }
}

fun DatabaseInterface.upsertPaaminnelseAndEnqueue(
    sykmeldt: Sykmeldt,
    sykmeldingsperiodeId: UUID,
    availableAt: Instant,
): Unit = connection.use { connection ->
    val paaminnelse = activatePaaminnelse(
        connection = connection,
        sykmeldt = sykmeldt,
        sykmeldingsperiodeId = sykmeldingsperiodeId,
    )
    if (paaminnelse == null) {
        connection.commit()
        return
    }
    connection.enqueueOutboxMessage(
        NewOutboxMessage(
            messageType = OppfolgingsplanOutboxMessageType.PAAMINNELSE_ARBEIDSGIVER,
            dedupKey = "${paaminnelse.uuid}:$sykmeldingsperiodeId:${UUID.randomUUID()}",
            externalRef = paaminnelse.uuid.toString(),
            payload = configuredJacksonMapper.writeValueAsString(
                PaaminnelseOutboxPayload(
                    sykmeldingsperiodeId = sykmeldingsperiodeId,
                ),
            ),
            availableAt = availableAt,
        ),
    )
    connection.enqueueOutboxMessage(
        NewOutboxMessage(
            messageType = OppfolgingsplanOutboxMessageType.PAAMINNELSE_DINE_SYKMELDTE,
            dedupKey = "${paaminnelse.uuid}:$sykmeldingsperiodeId:${UUID.randomUUID()}",
            externalRef = paaminnelse.uuid.toString(),
            payload = configuredJacksonMapper.writeValueAsString(
                PaaminnelseOutboxPayload(
                    sykmeldingsperiodeId = sykmeldingsperiodeId,
                ),
            ),
            availableAt = availableAt,
        ),
    )
    connection.commit()
}

fun DatabaseInterface.deactivatePaaminnelseAndCancelOutbox(
    sykmeldt: Sykmeldt,
    sykmeldingsperiodeId: UUID,
    completedAt: Instant,
): Unit = connection.use { connection ->
    val paaminnelse = upsertPaaminnelse(
        connection = connection,
        sykmeldt = sykmeldt,
        bestilt = false,
        sykmeldingsperiodeId = sykmeldingsperiodeId,
    )
    connection.cancelReadyOutboxMessages(
        messageType = OppfolgingsplanOutboxMessageType.PAAMINNELSE_ARBEIDSGIVER,
        externalRef = paaminnelse.uuid.toString(),
        reason = OutboxCancellationReason.NO_LONGER_REQUESTED,
        completedAt = completedAt,
    )
    connection.cancelReadyOutboxMessages(
        messageType = OppfolgingsplanOutboxMessageType.PAAMINNELSE_DINE_SYKMELDTE,
        externalRef = paaminnelse.uuid.toString(),
        reason = OutboxCancellationReason.NO_LONGER_REQUESTED,
        completedAt = completedAt,
    )
    connection.commit()
}

private fun activatePaaminnelse(
    connection: java.sql.Connection,
    sykmeldt: Sykmeldt,
    sykmeldingsperiodeId: UUID,
): PersistedPaaminnelse? {
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
        return if (resultSet.next()) resultSet.toPersistedPaaminnelse() else null
    }
}

private fun upsertPaaminnelse(
    connection: java.sql.Connection,
    sykmeldt: Sykmeldt,
    bestilt: Boolean,
    sykmeldingsperiodeId: UUID,
): PersistedPaaminnelse {
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
        check(resultSet.next()) { "upsertPaaminnelse returned no row" }
        return resultSet.toPersistedPaaminnelse()
    }
}

fun DatabaseInterface.findPaaminnelseBy(
    sykmeldtFnr: String,
    organisasjonsnummer: String,
): PersistedPaaminnelse? {
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
                resultSet.toPersistedPaaminnelse()
            } else {
                null
            }
        }
    }
}

fun DatabaseInterface.findPaaminnelseBy(uuid: UUID): PersistedPaaminnelse? {
    val statement = "SELECT * FROM paaminnelse WHERE uuid = ?"

    connection.use { connection ->
        connection.prepareStatement(statement).use { preparedStatement ->
            preparedStatement.setObject(1, uuid)
            val resultSet = preparedStatement.executeQuery()

            return if (resultSet.next()) {
                resultSet.toPersistedPaaminnelse()
            } else {
                null
            }
        }
    }
}

private fun ResultSet.toPersistedPaaminnelse(): PersistedPaaminnelse = PersistedPaaminnelse(
    uuid = getObject("uuid", UUID::class.java),
    organisasjonsnummer = getString("organisasjonsnummer"),
    sykmeldtFnr = getString("sykmeldt_fnr"),
    bestilt = getBoolean("bestilt"),
    sykmeldingsperiodeId = getObject("sykmeldingsperiode_id", UUID::class.java),
    createdAt = getTimestamp("created_at").toInstant(),
    updatedAt = getTimestamp("updated_at").toInstant(),
)
