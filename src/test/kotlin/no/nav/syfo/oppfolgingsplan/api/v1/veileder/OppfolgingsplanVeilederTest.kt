package no.nav.syfo.oppfolgingsplan.api.v1.veileder

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.syfo.defaultPersistedOppfolgingsplan
import java.time.Instant
import java.time.temporal.ChronoUnit

class OppfolgingsplanVeilederTest : DescribeSpec({
    describe("Extension function tests") {
        it("from with OppfolgingsplanMetadata should pick sistEndret based in provided date") {
            // Arrange
            val deltMedVeilederLast = defaultPersistedOppfolgingsplan().copy(
                deltMedVeilederTidspunkt = Instant.now().plus(10, ChronoUnit.MINUTES),
                skalDelesMedVeileder = true,
                deltMedLegeTidspunkt = Instant.now().plus(5, ChronoUnit.MINUTES),
            )
            val deletMedLegeLast = defaultPersistedOppfolgingsplan().copy(
                deltMedVeilederTidspunkt = Instant.now().plus(10, ChronoUnit.MINUTES),
                skalDelesMedVeileder = true,
                deltMedLegeTidspunkt = Instant.now().plus(15, ChronoUnit.MINUTES),
            )

            // Act
            val veilderLast = OppfolgingsplanVeileder.from(deltMedVeilederLast)
            val legeLast = OppfolgingsplanVeileder.from(deletMedLegeLast)

            // Assert
            veilderLast.sistEndret shouldBe deltMedVeilederLast.deltMedVeilederTidspunkt
            legeLast.sistEndret shouldBe deletMedLegeLast.deltMedLegeTidspunkt
        }
    }
})
