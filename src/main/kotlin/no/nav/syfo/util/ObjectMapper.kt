package no.nav.syfo.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * Applies standard Jackson configuration used across the application.
 * Use this function to configure any ObjectMapper consistently.
 */
fun ObjectMapper.applyStandardConfiguration(): ObjectMapper = apply {
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}

/**
 * Pre-configured Jackson ObjectMapper for use in services and utilities.
 * For Ktor ContentNegotiation, use [applyStandardConfiguration] instead.
 */
val configuredJacksonMapper: ObjectMapper = jacksonObjectMapper().applyStandardConfiguration()
