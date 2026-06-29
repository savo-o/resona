package com.savoo.scclient.auth

object OAuthConfig {
    const val REDIRECT_URI = "scclient://oauth/callback"
    const val AUTHORIZE_URL = "https://secure.soundcloud.com/authorize"
    const val TOKEN_URL = "https://secure.soundcloud.com/oauth/token"

    fun buildAuthorizeUrl(clientId: String): String =
        "$AUTHORIZE_URL?client_id=$clientId" +
            "&redirect_uri=$REDIRECT_URI" +
            "&response_type=code" +
            "&scope=non-expiring"
}
