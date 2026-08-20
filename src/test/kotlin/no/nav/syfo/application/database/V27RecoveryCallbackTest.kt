package no.nav.syfo.application.database

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.syfo.PsqlContainer
import org.flywaydb.core.Flyway
import java.sql.DriverManager
import java.util.UUID

class V27RecoveryCallbackTest :
    FunSpec({
        test("records canonical V27 and V28 history only when their complete schemas already exist") {
            PsqlContainer().use { container ->
                container.start()

                fun flyway(target: String) = Flyway.configure()
                    .dataSource(container.jdbcUrl, container.username, container.password)
                    .target(target)
                    .load()

                flyway("26").migrate()

                val sykmeldingsperiodeId = UUID.randomUUID()
                DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { connection ->
                    connection.createStatement().use { statement ->
                        statement.execute(
                            """
                            ALTER TABLE paaminnelse
                                ADD COLUMN sykmeldingsperiode_id UUID NOT NULL REFERENCES sykmeldingsperiode(id);
                            ALTER TABLE paaminnelse
                                DROP COLUMN outbox_at;
                            ALTER TABLE unntaksvurdering
                                ADD COLUMN organisasjonsnavn TEXT;
                            """.trimIndent(),
                        )
                    }
                    connection.prepareStatement(
                        """
                        INSERT INTO sykmeldingsperiode (
                            id, sykmeldt_fnr, organisasjonsnummer, sykmelding_id, fom, tom
                        ) VALUES (?, '12345678901', '123456789', 'sykmelding-id', '2026-01-01', '2026-01-31')
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setObject(1, sykmeldingsperiodeId)
                        statement.executeUpdate()
                    }
                    connection.prepareStatement(
                        """
                        INSERT INTO paaminnelse (
                            organisasjonsnummer, sykmeldt_fnr, bestilt, sykmeldingsperiode_id
                        ) VALUES ('123456789', '12345678901', TRUE, ?)
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setObject(1, sykmeldingsperiodeId)
                        statement.executeUpdate()
                    }
                }

                flyway("28").migrate().migrationsExecuted shouldBe 0

                DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { connection ->
                    connection.createStatement().use { statement ->
                        statement.executeQuery(
                            """
                            SELECT
                                (SELECT COUNT(*) FROM paaminnelse),
                                EXISTS (
                                    SELECT 1 FROM flyway_schema_history
                                    WHERE version = '27'
                                      AND description = 'update paaminnelse table'
                                      AND type = 'SQL'
                                      AND script = 'V27__update_paaminnelse_table.sql'
                                      AND checksum = -836683551
                                      AND success
                                ),
                                EXISTS (
                                    SELECT 1 FROM flyway_schema_history
                                    WHERE version = '28'
                                      AND description = 'add organisasjonsnavn to unntaksvurdering'
                                      AND type = 'SQL'
                                      AND script = 'V28__add_organisasjonsnavn_to_unntaksvurdering.sql'
                                      AND checksum = 954084108
                                      AND success
                                )
                            """.trimIndent(),
                        ).use { result ->
                            result.next() shouldBe true
                            result.getLong(1) shouldBe 1
                            result.getBoolean(2) shouldBe true
                            result.getBoolean(3) shouldBe true
                        }
                    }
                }

                flyway("28").migrate().migrationsExecuted shouldBe 0
            }
        }
    })
