package no.nav.syfo.application.kafka

import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Serializer

class JacksonKafkaSerializer : Serializer<Any> {
    override fun serialize(topic: String?, data: Any?): ByteArray = configuredJacksonMapper.writeValueAsBytes(data)
    override fun close() {}
}
