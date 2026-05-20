package no.nav.syfo.oppfolgingsplan.task

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class SoftDeleteOppfolgingsplanerTaskIntervalTest :
    DescribeSpec({
        describe("intervalForEnvironment") {
            it("should use prod interval in prod and short interval elsewhere") {
                SoftDeleteOppfolgingsplanerTask.intervalForEnvironment(isProdEnv = true) shouldBe 1.days
                SoftDeleteOppfolgingsplanerTask.intervalForEnvironment(isProdEnv = false) shouldBe 5.minutes
            }
        }
    })
