package no.nav.syfo.texas.client

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@Serializable
data class TexasIntrospectionRequest(
    @SerialName("identity_provider")
    val identityProvider: String,
    val token: String,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class TexasIntrospectionResponse(
    val active: Boolean,
    val error: String? = null,
    val pid: String? = null,
    val acr: String? = null,
    val aud: String? = null,
    val azp: String? = null,
    val exp: Long? = null,
    val iat: Long? = null,
    val iss: String? = null,
    val jti: String? = null,
    val nbf: Long? = null,
    val sub: String? = null,
    val tid: String? = null,
)