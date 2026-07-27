package no.nav.syfo.varsel.budstikka.domain

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import no.nav.syfo.varsel.budstikka.infrastructure.OPPFOLGINGSPLAN_CREATED_BUDSTIKKA_TEXT

class DispatchSerializationTest :
    DescribeSpec({
        describe("dispatchJson") {
            it("serializes BrukervarselCreate with stable type name and inline personIdentifier") {
                val dispatch = Dispatch(
                    reference = "0a5c80b8-2350-4f2a-b0e7-d1b796c6c8d4",
                    content = BrukervarselCreate(
                        personIdentifier = PersonIdentifier("12345678901"),
                        varseltype = Varseltype.BESKJED,
                        text = OPPFOLGINGSPLAN_CREATED_BUDSTIKKA_TEXT,
                    ),
                )

                dispatchJson.encodeToString(dispatch) shouldBe
                    """{"reference":"0a5c80b8-2350-4f2a-b0e7-d1b796c6c8d4","content":{"type":"BrukervarselCreate","personIdentifier":"12345678901","varseltype":"BESKJED","text":"$OPPFOLGINGSPLAN_CREATED_BUDSTIKKA_TEXT","link":null,"visibleUntil":null,"externalVarsling":null,"brevFallback":null,"sendingWindow":null}}"""
            }

            it("does not serialize partitionKey or eventId") {
                val dispatch = Dispatch(
                    reference = "0a5c80b8-2350-4f2a-b0e7-d1b796c6c8d4",
                    content = BrukervarselCreate(
                        personIdentifier = PersonIdentifier("12345678901"),
                        varseltype = Varseltype.BESKJED,
                        text = OPPFOLGINGSPLAN_CREATED_BUDSTIKKA_TEXT,
                    ),
                )

                dispatchJson.encodeToString(dispatch) shouldNotContain "partitionKey"
                dispatchJson.encodeToString(dispatch) shouldNotContain "eventId"
            }
        }

        describe("PII masking") {
            it("masks PersonIdentifier in toString") {
                PersonIdentifier("12345678901").toString() shouldBe "***"
            }
        }
    })
