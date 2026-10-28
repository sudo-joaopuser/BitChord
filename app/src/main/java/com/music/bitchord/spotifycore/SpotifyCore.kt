package com.music.bitchord.spotifycore

import java.security.MessageDigest
import java.util.Base64
import java.security.SecureRandom

/**
 * Clean Spotify core: official Web API + App Remote only.
 *
 * No librespot, no session cookie, no partner GraphQL, no page scraping,
 * no audio download or decryption. Playback happens in the official
 * Spotify app (App Remote) or on an active Connect device (Web API
 * Player). This file is pure Kotlin with no Spotify SDK dependency, so
 * the app compiles without the proprietary App Remote AAR; the AAR
 * implementation plugs in behind SpotifyAppRemotePlayer later.
 */

data class SpotifyTrack(
    val id: String,
    val uri: String,
    val title: String,
    val artists: List<String>,
    val album: String?,
    val durationMs: Long,
    val imageUrl: String?,
    val isPlayable: Boolean = true,
)

data class SpotifyPlaylist(
    val id: String,
    val uri: String,
    val name: String,
    val owner: String?,
    val imageUrl: String?,
    val totalTracks: Int,
)

data class SpotifyDevice(
    val id: String?,
    val name: String,
    val type: String,
    val isActive: Boolean,
)

data class SpotifyPlaybackState(
    val track: SpotifyTrack?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val device: SpotifyDevice?,
    val shuffle: Boolean,
    val repeat: String,
)

data class SpotifyTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMs: Long,
) {
    val isExpired: Boolean get() = System.currentTimeMillis() >= expiresAtMs - 60_000
}

/** Authorization Code with PKCE. No client secret lives in the app. */
object SpotifyPkce {
    fun codeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}

class SpotifyApiException(val status: Int, message: String) : Exception(message)
class SpotifyPremiumRequiredException : Exception("Spotify Premium required")

/** Turns a Spotify/network failure into the message the UI shows: the
 * API status when there is one, so a 401/403/429 is never rendered as
 * a silent empty library, and a timeout says so instead of hanging. */
fun formatSpotifyError(e: Throwable): String = when (e) {
    is SpotifyApiException ->
        if (e.status > 0) "Spotify (${e.status}): ${e.message}".trim()
        else e.message?.takeIf { it.isNotBlank() } ?: "Falha no Spotify"
    is SpotifyPremiumRequiredException -> "Spotify Premium necessário"
    is java.net.SocketTimeoutException ->
        "Tempo esgotado ao falar com o Spotify (timeout) — verifique a conexão e tente de novo"
    else -> e.message?.takeIf { it.isNotBlank() } ?: "Falha ao falar com o Spotify"
}

/** Minimal scopes for the clean core. Request only these, nothing broader. */
val SPOTIFY_SCOPES = listOf(
    "user-read-private",
    "user-library-read",
    "playlist-read-private",
    "playlist-read-collaborative",
    "user-read-playback-state",
    "user-modify-playback-state",
    "user-read-currently-playing",
    "user-read-recently-played",
    "user-top-read",
    "app-remote-control",
    "streaming",
)

fun interface TokenSource {
    suspend fun accessToken(): String
}

/**
 * Authorization Code with PKCE, step by step, so the login screen can
 * ask for the Developer Client ID first and only then open Spotify:
 * authorizeUrl -> user approves in browser -> exchangeCode -> tokens.
 * No client secret lives in the app.
 */
interface SpotifyAuth : TokenSource {
    fun authorizeUrl(clientId: String, redirectUri: String, verifier: String): String
    suspend fun exchangeCode(code: String, clientId: String, redirectUri: String, verifier: String): SpotifyTokens
    suspend fun refresh(tokens: SpotifyTokens, clientId: String): SpotifyTokens
    suspend fun ensureValid(clientId: String): SpotifyTokens?
    suspend fun logout()
}

interface SpotifyCatalog {
    suspend fun profile(): String
    suspend fun profileInfo(): SpotifyProfile
    suspend fun playlists(limit: Int = 50): List<SpotifyPlaylist>
    suspend fun playlistsTotal(): Int
    suspend fun playlistTracks(playlistId: String): List<SpotifyTrack>
    suspend fun likedTracks(): List<SpotifyTrack>
    suspend fun likedTotal(): Int
    suspend fun topTracks(limit: Int = 50): List<SpotifyTrack>
    suspend fun search(query: String, limit: Int = 10, market: String? = null): List<SpotifyTrack>
    suspend fun albumTracks(albumId: String): List<SpotifyTrack>
    suspend fun artistTopTracks(artistId: String): List<SpotifyTrack>
}

data class SpotifyProfile(val name: String, val id: String, val country: String? = null)

/** Web API Player: controls an active Connect device. Premium only. */
interface SpotifyPlayer {
    suspend fun devices(): List<SpotifyDevice>
    suspend fun playbackState(): SpotifyPlaybackState?
    suspend fun play(uris: List<String>, deviceId: String? = null)
    suspend fun pause()
    suspend fun next()
    suspend fun previous()
    suspend fun seek(positionMs: Long)
    suspend fun setShuffle(enabled: Boolean)
    suspend fun transfer(deviceId: String)
}

/** App Remote: controls the official Spotify app on this phone. */
interface SpotifyAppRemotePlayer {
    suspend fun connect(clientId: String, redirectUri: String)
    suspend fun disconnect()
    suspend fun play(uri: String)
    suspend fun pause()
    suspend fun resume()
    suspend fun skipNext()
    suspend fun skipPrevious()
    suspend fun seek(positionMs: Long)
    suspend fun canPlayOnDemand(): Boolean
}
