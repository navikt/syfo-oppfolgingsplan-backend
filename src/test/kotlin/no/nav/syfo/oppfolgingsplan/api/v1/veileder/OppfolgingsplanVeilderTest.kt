package no.nav.syfo.oppfolgingsplan.api.v1.veileder

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.temporal.ChronoUnit
import no.nav.syfo.defaultPersistedOppfolgingsplan
import no.nav.syfo.oppfolgingsplan.api.v1.veilder.OppfolgingsplanVeilder
import no.nav.syfo.oppfolgingsplan.dto.mapToOppfolgingsplanMetadata

class OppfolgingsplanVeilderTest : DescribeSpec({
    describe("Extension function tests") {
        it("from with OppfolgingsplanMetadata should pick sistEndret based in provided date") {
            // Arrange
            val deltMedVeilederLast = defaultPersistedOppfolgingsplan().mapToOppfolgingsplanMetadata().copy(
                deltMedVeilederTidspunkt = Instant.now().plus(10, ChronoUnit.MINUTES),
                skalDelesMedVeileder = true,
                deltMedLegeTidspunkt = Instant.now().plus(5, ChronoUnit.MINUTES),
            )
            val deletMedLegeLast = defaultPersistedOppfolgingsplan().mapToOppfolgingsplanMetadata().copy(
                deltMedVeilederTidspunkt = Instant.now().plus(10, ChronoUnit.MINUTES),
                skalDelesMedVeileder = true,
                deltMedLegeTidspunkt = Instant.now().plus(15, ChronoUnit.MINUTES),
            )

            // Act
            val veilderLast = OppfolgingsplanVeilder.from(deltMedVeilederLast)
            val legeLast = OppfolgingsplanVeilder.from(deletMedLegeLast)

            // Assert
            veilderLast.sistEndret shouldBe deltMedVeilederLast.deltMedVeilederTidspunkt
            legeLast.sistEndret shouldBe deletMedLegeLast.deltMedLegeTidspunkt
        }
    }
})
