package no.nav.syfo.oppfolgingsplan.db

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.syfo.defaultPersistedOppfolgingsplanUtkast
import no.nav.syfo.defaultSykmeldt
import no.nav.syfo.oppfolgingsplan.db.domain.toResponse
import no.nav.syfo.oppfolgingsplan.db.domain.toUtkastMetadata
import no.nav.syfo.oppfolgingsplan.db.domain.utkastUtloperDato
import java.time.Instant

class PersistedOppfolgingsplanUtkastTest :
    DescribeSpec({
        describe("PersistedOppfolgingsplanUtkast") {
            it("should calculate utkastUtloperDato in UTC from updatedAt") {
                val updatedAt = Instant.parse("2025-01-15T10:30:00Z")
                val utkast = defaultPersistedOppfolgingsplanUtkast().copy(updatedAt = updatedAt)

                utkast.utkastUtloperDato() shouldBe Instant.parse("2025-05-15T10:30:00Z")
            }

            it("should expose utkastUtloperDato in metadata and response dto") {
                val updatedAt = Instant.parse("2025-01-15T10:30:00Z")
                val utkast = defaultPersistedOppfolgingsplanUtkast().copy(updatedAt = updatedAt)
                val expectedExpiry = Instant.parse("2025-05-15T10:30:00Z")

                utkast.toUtkastMetadata().utkastUtloperDato shouldBe expectedExpiry
                utkast.toResponse(defaultSykmeldt()).utkast?.utkastUtloperDato shouldBe expectedExpiry
            }
        }
    })
