package no.nav.syfo.application.valkey

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.Jedis

class ValkeyCache(
    valkeyEnvironment: ValkeyEnvironment,
) {

    private val jedis = Jedis(
        valkeyEnvironment.valkeyHost,
        valkeyEnvironment.valkeyPort,
        DefaultJedisClientConfig
            .builder()
            .user(valkeyEnvironment.username)
            .password(valkeyEnvironment.password)
            .build()
    )

    fun <T> get(key: String, type: Class<T>): T? {
        val json = jedis.get(key)
        return if (json != null) {
            jacksonObjectMapper().readValue(json, type)
        } else {
            null
        }
    }

    fun <T> put(key: String, value: T, ttlSeconds: Long = 3600) {
        val json = jacksonObjectMapper().writeValueAsString(value)
        jedis.setex(key, ttlSeconds, json)
    }
}
