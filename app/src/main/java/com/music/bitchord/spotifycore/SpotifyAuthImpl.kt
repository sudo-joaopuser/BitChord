package com.music.bitchord.spotifycore

import android.content.Context
import android.content.SharedPreferences
import java.net.URLEncoder

/** Token storage. Swap for EncryptedPrefs in the app wiring, same contract. */
interface TokenStore {
    fun save(tokens: SpotifyTokens, clientId: String)
    fun load(): Pair<SpotifyTokens, String>?
    fun clear()
}

class SharedPrefsTokenStore(context: Context) : TokenStore {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("spotify_core", Context.MODE_PRIVATE)

    override fun save(tokens: SpotifyTokens, clientId: String) {
        prefs.edit()
            .putString("access", tokens.accessToken)
            .putString("refresh", tokens.refreshToken)
            .putLong("expires_at", tokens.expiresAtMs)
            .putString("client_id", clientId)
            .apply()
    }

    override fun load(): Pair<SpotifyTokens, String>? {
        val access = prefs.getString("access", null) ?: return null
        val clientId = prefs.getString("client_id", null) ?: return null
        return SpotifyTokens(
            accessToken = access,
            refreshToken = prefs.getString("refresh", null),
            expiresAtMs = prefs.getLong("expires_at", 0L),
        ) to clientId
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}

class SpotifyAuthImpl(private val store: TokenStore) : SpotifyAuth {
    override fun authorizeUrl(clientId: String, redirectUri: String, verifier: String): String {
        val challenge = SpotifyPkce.codeChallenge(verifier)
        val params = mapOf(
            "client_id" to clientId,
            "response_type" to "code",
            "redirect_uri" to redirectUri,
            "code_challenge_method" to "S256",
            "code_challenge" to challenge,
            "scope" to SPOTIFY_SCOPES.joinToString(" "),
        )
        val query = params.entries.joinToString("&") { (k, v) ->
            URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
        }
        return "https://accounts.spotify.com/authorize?$query"
    }

    override suspend fun exchangeCode(
        code: String, clientId: String, redirectUri: String, verifier: String,
    ): SpotifyTokens {
        val json = SpotifyHttp.tokenRequest(
            SpotifyHttp.form(
                mapOf(
                    "grant_type" to "authorization_code",
                    "code" to code,
                    "redirect_uri" to redirectUri,
                    "client_id" to clientId,
                    "code_verifier" to verifier,
                )
            )
        )
        val tokens = tokensFrom(json)
        store.save(tokens, clientId)
        return tokens
    }

    override suspend fun refresh(tokens: SpotifyTokens, clientId: String): SpotifyTokens {
        val refreshToken = tokens.refreshToken
            ?: throw SpotifyApiException(400, "No refresh token stored")
        val json = SpotifyHttp.tokenRequest(
            SpotifyHttp.form(
                mapOf(
                    "grant_type" to "refresh_token",
                    "refresh_token" to refreshToken,
                    "client_id" to clientId,
                )
            )
        )
        val fresh = tokensFrom(json, fallbackRefresh = refreshToken)
        store.save(fresh, clientId)
        return fresh
    }

    override suspend fun ensureValid(clientId: String): SpotifyTokens? {
        val (tokens, _) = store.load() ?: return null
        if (!tokens.isExpired) return tokens
        return runCatching { refresh(tokens, clientId) }.getOrNull()
    }

    override suspend fun logout() {
        store.clear()
    }

    override suspend fun accessToken(): String {
        val (tokens, clientId) = store.load()
            ?: throw SpotifyApiException(401, "Not signed in")
        if (!tokens.isExpired) return tokens.accessToken
        return refresh(tokens, clientId).accessToken
    }

    private fun tokensFrom(json: org.json.JSONObject, fallbackRefresh: String? = null): SpotifyTokens {
        val expiresIn = json.optLong("expires_in", 3600)
        val refreshStr = if (json.isNull("refresh_token")) null
            else json.optString("refresh_token").takeIf { it.isNotBlank() }
        return SpotifyTokens(
            accessToken = json.getString("access_token"),
            refreshToken = refreshStr ?: fallbackRefresh,
            expiresAtMs = System.currentTimeMillis() + expiresIn * 1000,
        )
    }
}
