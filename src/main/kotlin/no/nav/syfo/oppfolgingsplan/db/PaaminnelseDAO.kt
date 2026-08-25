package no.nav.syfo.oppfolgingsplan.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.outbox.db.enqueueOutboxMessage
import no.nav.syfo.application.outbox.domain.NewOutboxMessage
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedPaaminnelse
import no.nav.syfo.oppfolgingsplan.outbox.OppfolgingsplanOutboxMessageType
import no.nav.syfo.util.configuredJacksonMapper
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

data class PaaminnelseOutboxPayload(
    val sykmeldingsperiodeId: UUID,
    val narmestelederId: String,
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
    narmestelederId: String,
    availableAt: Instant,
): PersistedPaaminnelse = connection.use { connection ->
    val paaminnelse = upsertPaaminnelse(
        connection = connection,
        sykmeldt = sykmeldt,
        bestilt = true,
        sykmeldingsperiodeId = sykmeldingsperiodeId,
    )
    connection.enqueueOutboxMessage(
        NewOutboxMessage(
            messageType = OppfolgingsplanOutboxMessageType.PAAMINNELSE,
            dedupKey = "${paaminnelse.uuid}:$sykmeldingsperiodeId",
            externalRef = paaminnelse.uuid.toString(),
            payload = configuredJacksonMapper.writeValueAsString(
                PaaminnelseOutboxPayload(
                    sykmeldingsperiodeId = sykmeldingsperiodeId,
                    narmestelederId = narmestelederId,
                ),
            ),
            availableAt = availableAt,
        ),
    )
    connection.enqueueOutboxMessage(
        NewOutboxMessage(
            messageType = OppfolgingsplanOutboxMessageType.PAAMINNELSE_DINE_SYKMELDTE,
            dedupKey = "${paaminnelse.uuid}:$sykmeldingsperiodeId",
            externalRef = paaminnelse.uuid.toString(),
            payload = configuredJacksonMapper.writeValueAsString(
                PaaminnelseOutboxPayload(
                    sykmeldingsperiodeId = sykmeldingsperiodeId,
                    narmestelederId = narmestelederId,
                ),
            ),
            availableAt = availableAt,
        ),
    )
    connection.commit()
    paaminnelse
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
