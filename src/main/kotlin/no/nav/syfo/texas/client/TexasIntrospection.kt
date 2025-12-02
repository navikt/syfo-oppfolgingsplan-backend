package no.nav.syfo.texas.client

import com.fasterxml.jackson.annotation.JsonProperty


data class TexasIntrospectionRequest(
    @get:JsonProperty("identity_provider")
    val identityProvider: String,
    val token: String,
)

@Suppress("ConstructorParameterNaming")
data class TexasIntrospectionResponse(
    val active: Boolean,
    val error: String? = null,
    val pid: String? = null,
    val acr: String? = null,
    val aud: String? = null,
    @get:JsonProperty("client_id")
    val clientId: String? = null,
    @get:JsonProperty("azp_name")
    val azpName: String? = null,
    val exp: Long? = null,
    val iat: Long? = null,
    val iss: String? = null,
    val jti: String? = null,
    val nbf: Long? = null,
    val sub: String? = null,
    val tid: String? = null,
    val NAVident: String? = null,
)
