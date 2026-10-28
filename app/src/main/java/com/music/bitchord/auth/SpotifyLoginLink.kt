package com.music.bitchord.auth

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Relay for the Spotify OAuth return, same pattern as PlayerDeepLink.
 *
 * The login screen opens Spotify in the browser with redirect URI
 * bitchord-spotify://callback. Spotify returns with ?code=... in the
 * intent data. The activity cannot reach the Compose login state, so
 * this object holds the code until the composition exchanges it.
 */
object SpotifyLoginLink {
    private val _pendingCode = MutableStateFlow<String?>(null)
    val pendingCode: StateFlow<String?> = _pendingCode.asStateFlow()

    fun consume(intent: Intent?): Boolean {
        val data = intent?.data ?: return false
        if (data.scheme != "bitchord-spotify") return false
        val code = data.getQueryParameter("code") ?: return false
        _pendingCode.value = code
        return true
    }

    fun handled() {
        _pendingCode.value = null
    }
}
