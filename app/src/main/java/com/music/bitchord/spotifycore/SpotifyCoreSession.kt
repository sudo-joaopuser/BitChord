package com.music.bitchord.spotifycore

import android.content.Context

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

    /** Drops the saved tokens and the in-memory session together, so a
     * broken session can never stay marked as connected. */
    fun clear(context: Context) {
        SharedPrefsTokenStore(context.applicationContext).clear()
        auth = null
        catalog = null
        player = null
        appRemotePlayer = null
    }

    /**
     * Rebuilds the session from saved tokens when the catalog is missing
     * (e.g. the connected flag survived a recomposition but the catalog
     * did not). Returns true when a session is ready afterwards.
     */
    fun restore(context: Context): Boolean {
        if (catalog != null) return isConnected
        val store = SharedPrefsTokenStore(context.applicationContext)
        if (store.load() == null) return false
        val auth = SpotifyAuthImpl(store)
        this.auth = auth
        catalog = SpotifyCatalogImpl(auth)
        player = SpotifyConnectPlayer(auth)
        return true
    }
}
