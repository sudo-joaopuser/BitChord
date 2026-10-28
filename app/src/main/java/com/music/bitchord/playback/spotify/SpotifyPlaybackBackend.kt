package com.music.bitchord.playback.spotify

/**
 * Spotify-only playback contract for the spotify-only fork.
 *
 * Spotify over librespot does not hand back a stream URL, so this backend
 * cannot be a MusicSource: it owns the session, decodes via the Rust core
 * (librespot lineage, see psst-core) and delivers PCM to Android audio.
 * The JNI implementation is not wired yet — this file only fixes the
 * boundary the rest of the app will talk to, so the YouTube path can be
 * removed without guessing the interface later.
 */
data class SpotifyTrackRef(
    val uri: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val imageUrl: String?,
)

sealed interface SpotifyPlaybackState {
    data object Idle : SpotifyPlaybackState
    data object Loading : SpotifyPlaybackState
    data class Playing(val track: SpotifyTrackRef, val positionMs: Long) : SpotifyPlaybackState
    data class Paused(val track: SpotifyTrackRef, val positionMs: Long) : SpotifyPlaybackState
    data class Failed(val reason: String) : SpotifyPlaybackState
}

interface SpotifyPlaybackBackend {
    /** Starts a Premium session. Throws when the account is not Premium. */
    suspend fun connect(oauthAccessToken: String)

    suspend fun play(track: SpotifyTrackRef)
    suspend fun pause()
    suspend fun resume()
    suspend fun seekTo(positionMs: Long)
    suspend fun stop()
    suspend fun disconnect()
}
