package no.nav.syfo.varsel.budstikka.domain

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.util.UUID

class DispatchSerializationTest :
    DescribeSpec({
        describe("dispatchJson") {
            it("serializes BrukervarselCreate with stable type name and inline personIdentifier") {
                val dispatch = Dispatch(
                    eventId = UUID.fromString("5fbc039e-b104-4554-809f-337d7ef804d0"),
                    reference = "0a5c80b8-2350-4f2a-b0e7-d1b796c6c8d4",
                    content = BrukervarselCreate(
                        personIdentifier = PersonIdentifier("12345678901"),
                        varseltype = Varseltype.BESKJED,
                        text = "Det er opprettet en oppfølgingsplan.",
                    ),
                )

                dispatchJson.encodeToString(dispatch) shouldBe
                    """{"eventId":"5fbc039e-b104-4554-809f-337d7ef804d0","reference":"0a5c80b8-2350-4f2a-b0e7-d1b796c6c8d4","content":{"type":"BrukervarselCreate","personIdentifier":"12345678901","varseltype":"BESKJED","text":"Det er opprettet en oppfølgingsplan.","link":null,"visibleUntil":null,"externalVarsling":null,"brevFallback":null,"sendingWindow":null}}"""
            }

            it("does not serialize partitionKey") {
                val dispatch = Dispatch(
                    eventId = UUID.fromString("5fbc039e-b104-4554-809f-337d7ef804d0"),
                    reference = "0a5c80b8-2350-4f2a-b0e7-d1b796c6c8d4",
                    content = BrukervarselCreate(
                        personIdentifier = PersonIdentifier("12345678901"),
                        varseltype = Varseltype.BESKJED,
                        text = "Det er opprettet en oppfølgingsplan.",
                    ),
                )

                dispatchJson.encodeToString(dispatch) shouldNotContain "partitionKey"
            }
        }

        describe("PII masking") {
            it("masks PersonIdentifier in toString") {
                PersonIdentifier("12345678901").toString() shouldBe "***"
            }

            it("keeps raw personIdentifier value in serialized payload") {
                val json = dispatchJson.encodeToString(
                    Dispatch(
                        eventId = UUID.fromString("5fbc039e-b104-4554-809f-337d7ef804d0"),
                        reference = "0a5c80b8-2350-4f2a-b0e7-d1b796c6c8d4",
                        content = BrukervarselCreate(
                            personIdentifier = PersonIdentifier("12345678901"),
                            varseltype = Varseltype.BESKJED,
                            text = "Det er opprettet en oppfølgingsplan.",
                        ),
                    ),
                )

                json shouldContain "\"personIdentifier\":\"12345678901\""
            }
        }
    })
