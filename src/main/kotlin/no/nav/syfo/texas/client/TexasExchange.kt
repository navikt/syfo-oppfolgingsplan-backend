package no.nav.syfo.texas.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TexasExchangeRequest(
    @SerialName("identity_provider")
    val identityProvider: String,
    val target: String,
    @SerialName("user_token")
    val userToken: String,
)

@Serializable
data class TexasExchangeResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("expires_in")
    val expiresIn: Long,
    @SerialName("token_type")
    val tokenType: String,
)