package no.nav.syfo.oppfolgingsplan.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.outbox.db.insertOutbox
import no.nav.syfo.application.outbox.domain.OutboxMessageType
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.oppfolgingsplan.db.domain.PersistedPaaminnelse
import no.nav.syfo.oppfolgingsplan.outbox.PaaminnelsePayload
import no.nav.syfo.util.configuredJacksonMapper
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Dedup key for a påminnelse outbox message. Contains no PII: the påminnelse uuid is stable per
 * (orgnr, fnr), and forlopFom distinguishes one sykefraværsforløp from the next.
 */
fun paaminnelseDedupKey(paaminnelseUuid: UUID, forlopFom: LocalDate): String = "$paaminnelseUuid:$forlopFom"

fun DatabaseInterface.upsertPaaminnelse(
    sykmeldt: Sykmeldt,
    bestilt: Boolean,
    forlopFom: LocalDate,
): PersistedPaaminnelse = connection.use { connection ->
    upsertPaaminnelse(connection, sykmeldt, bestilt, forlopFom).also { connection.commit() }
}

/**
 * Orders a påminnelse and queues the outbox message in one transaction, so the two can never
 * disagree.
 */
fun DatabaseInterface.upsertPaaminnelseAndActivateOutbox(
    sykmeldt: Sykmeldt,
    forlopFom: LocalDate,
    scheduledAt: Instant,
): PersistedPaaminnelse = connection.use { connection ->
    val paaminnelse = upsertPaaminnelse(connection, sykmeldt, bestilt = true, forlopFom = forlopFom)

    connection.insertOutbox(
        messageType = OutboxMessageType.PAAMINNELSE_OPPFOLGINGSPLAN,
        dedupKey = paaminnelseDedupKey(paaminnelse.uuid, forlopFom),
        externalRef = paaminnelse.uuid.toString(),
        payload = configuredJacksonMapper.writeValueAsString(
            PaaminnelsePayload(
                messageType = OutboxMessageType.PAAMINNELSE_OPPFOLGINGSPLAN,
                forlopFom = forlopFom,
            ),
        ),
        scheduledAt = scheduledAt,
    )

    paaminnelse.also { connection.commit() }
}

fun DatabaseInterface.findPaaminnelseBy(
    sykmeldtFnr: String,
    organisasjonsnummer: String,
): PersistedPaaminnelse? = connection.use { connection ->
    connection.findPaaminnelseBy(sykmeldtFnr, organisasjonsnummer)
}

fun DatabaseInterface.findPaaminnelseForStatus(
    sykmeldtFnr: String,
    organisasjonsnummer: String,
    forlopFom: LocalDate,
): PersistedPaaminnelse? = connection.use { connection ->
    connection.findPaaminnelseBy(sykmeldtFnr, organisasjonsnummer)
        ?.takeIf { it.forlopFom == forlopFom }
}

/**
 * Transaction-scoped variant. Neither commits nor closes the connection.
 */
internal fun Connection.findPaaminnelseBy(
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

    var idx = 0
    prepareStatement(statement).use { preparedStatement ->
        preparedStatement.setString(++idx, sykmeldtFnr)
        preparedStatement.setString(++idx, organisasjonsnummer)
        preparedStatement.executeQuery().use { resultSet ->
            return if (resultSet.next()) resultSet.toPersistedPaaminnelse() else null
        }
    }
}

internal fun Connection.findPaaminnelseBy(uuid: UUID): PersistedPaaminnelse? {
    prepareStatement("SELECT * FROM paaminnelse WHERE uuid = ?").use { preparedStatement ->
        preparedStatement.setObject(1, uuid)
        preparedStatement.executeQuery().use { resultSet ->
            return if (resultSet.next()) resultSet.toPersistedPaaminnelse() else null
        }
    }
}

/**
 * Keeps the original conflict target `(organisasjonsnummer, sykmeldt_fnr)`. Switching to a triple
 * including `forlop_fom` would require dropping the existing unique constraint first, which would
 * break writes from pods still running the previous version during a rolling deploy.
 */
private fun upsertPaaminnelse(
    connection: Connection,
    sykmeldt: Sykmeldt,
    bestilt: Boolean,
    forlopFom: LocalDate,
): PersistedPaaminnelse {
    val statement =
        """
        INSERT INTO paaminnelse (
            organisasjonsnummer,
            sykmeldt_fnr,
            forlop_fom,
            bestilt,
            created_at,
            updated_at
        ) VALUES (?, ?, ?, ?, NOW(), NOW())
        ON CONFLICT (organisasjonsnummer, sykmeldt_fnr) DO UPDATE SET
            bestilt = EXCLUDED.bestilt,
            forlop_fom = EXCLUDED.forlop_fom,
            updated_at = NOW()
        RETURNING *
        """.trimIndent()

    var idx = 0
    connection.prepareStatement(statement).use { preparedStatement ->
        preparedStatement.setString(++idx, sykmeldt.orgnummer)
        preparedStatement.setString(++idx, sykmeldt.fnr)
        preparedStatement.setObject(++idx, forlopFom)
        preparedStatement.setBoolean(++idx, bestilt)
        preparedStatement.executeQuery().use { resultSet ->
            check(resultSet.next()) { "upsertPaaminnelse returnerte ingen rad" }
            return resultSet.toPersistedPaaminnelse()
        }
    }
}

private fun ResultSet.toPersistedPaaminnelse(): PersistedPaaminnelse = PersistedPaaminnelse(
    uuid = getObject("uuid", UUID::class.java),
    organisasjonsnummer = getString("organisasjonsnummer"),
    sykmeldtFnr = getString("sykmeldt_fnr"),
    forlopFom = getDate("forlop_fom").toLocalDate(),
    bestilt = getBoolean("bestilt"),
    createdAt = getTimestamp("created_at").toInstant(),
    updatedAt = getTimestamp("updated_at").toInstant(),
)
