package no.nav.syfo.oppfolgingsplan.service

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.temporal.ChronoUnit
import no.nav.syfo.defaultPersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.dto.mapToOppfolgingsplanMetadata

class OppfolginsplanServiceTest : DescribeSpec({
    describe("Extension function tests") {
        it("toListOppfolginsplanVeiler should filter out items not delt with veileder") {
            // Arrange
            val deltMedVeilder = defaultPersistedOppfolgingsplan().mapToOppfolgingsplanMetadata().copy(
                deltMedVeilederTidspunkt = Instant.now().plus(10, ChronoUnit.MINUTES),
                skalDelesMedVeileder = true
            )
            val ikkeDeltMedVeilder = defaultPersistedOppfolgingsplan().mapToOppfolgingsplanMetadata().copy(
                deltMedVeilederTidspunkt = null,
                skalDelesMedVeileder = false
            )
            val oppfolgingsplaner = listOf(ikkeDeltMedVeilder, deltMedVeilder)

            // Act
            val filtered = oppfolgingsplaner.toListOppfolginsplanVeiler()
            // Assert

            filtered.size shouldBe 1
            filtered.first().uuid shouldBe deltMedVeilder.uuid
        }
    }
})
