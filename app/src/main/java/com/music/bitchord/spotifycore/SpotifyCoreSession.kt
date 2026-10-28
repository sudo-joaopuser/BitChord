package com.music.bitchord.spotifycore

/**
 * Where the app keeps the clean core once it is wired up.
 *
 * Set once at startup / after login, read by SpotifySource for health
 * and search. Kept separate from the old SpotifyLibrary static auth so
 * no web-player token can leak into the clean path.
 */
object SpotifyCoreSession {
    @Volatile var auth: SpotifyAuth? = null
    @Volatile var catalog: SpotifyCatalog? = null
    @Volatile var player: SpotifyPlayer? = null
    @Volatile var appRemotePlayer: SpotifyAppRemotePlayer? = null

    val isConnected: Boolean get() = auth != null
}
