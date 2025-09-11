package no.nav.syfo.application.valkey

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.valkey.DefaultJedisClientConfig
import io.valkey.HostAndPort
import io.valkey.JedisPool
import io.valkey.JedisPoolConfig
import no.nav.syfo.dinesykmeldte.client.Sykmeldt
import no.nav.syfo.util.logger

class ValkeyCache(
    valkeyEnvironment: ValkeyEnvironment,
) {

    private val logger = logger()

    private val jedisPool = JedisPool(
        JedisPoolConfig(),
        HostAndPort(valkeyEnvironment.host, valkeyEnvironment.port),
        DefaultJedisClientConfig.builder()
            .ssl(valkeyEnvironment.ssl)
            .user(valkeyEnvironment.username)
            .password(valkeyEnvironment.password)
            .build()
    )

    private fun <T> get(key: String, type: Class<T>): T? {
        try {
            val jedis = jedisPool.resource
            val json = jedis.get(key)
            return json?.let {
                jacksonObjectMapper().readValue(it, type)
            }
        } catch (e: Exception) {
            logger.error("Failed to get from cache", e)
            return null
        } finally {
            jedisPool.resource.close()
        }
    }

    private fun <T> put(key: String, value: T, ttlSeconds: Long = CACHE_TTL_SECONDS) {
        try {
            val jedis = jedisPool.resource
            val json = jacksonObjectMapper().writeValueAsString(value)
            jedis.setex(key, ttlSeconds, json)
        } catch (e: Exception) {
            logger.error("Failed to put in cache", e)
        } finally {
            jedisPool.resource.close()
        }
    }

    fun getSykmeldt(lederFnr: String, narmestelederId: String): Sykmeldt? {
        val sykmeldt = get("$DINE_SYKMELDTE_CACHE_KEY_PREFIX-$lederFnr-$narmestelederId", Sykmeldt::class.java)
        if (sykmeldt != null) {
            COUNT_CACHE_HIT_DINE_SYKMELDTE.increment()
        }
        return sykmeldt
    }

    fun putSykmeldt(lederFnr: String, narmestelederId: String, sykmeldt: Sykmeldt) {
        put("$DINE_SYKMELDTE_CACHE_KEY_PREFIX-$lederFnr-$narmestelederId", sykmeldt)
    }

    companion object {
        const val CACHE_TTL_SECONDS = 3600L
        const val DINE_SYKMELDTE_CACHE_KEY_PREFIX = "dinesykmeldte"
    }
}
