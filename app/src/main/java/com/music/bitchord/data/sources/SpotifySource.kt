package com.music.bitchord.data.sources

import com.music.bitchord.data.model.Song
import com.music.bitchord.spotifycore.SpotifyCoreSession

/**
 * Spotify as a Source in the app, clean core only.
 *
 * Search lists tracks from the Web API so they show up in the app.
 * Stream intentionally returns null: clean Spotify has no stream URL
 * to hand to the old player — playback goes through App Remote or
 * Connect in the core player, not through SourceResolver. Returning
 * null is a miss, not a crash, and keeps the resolver moving on.
 */
class SpotifySource(
    override val config: SourceConfig,
) : MusicSource, SourceRegistry.ConfigBacked {

    override val configId: String get() = config.id
    override val kind: SourceKind get() = SourceKind.SPOTIFY
    override val displayName: String get() = config.label.ifBlank { SourceKind.SPOTIFY.label }

    override suspend fun health(): SourceHealth =
        if (SpotifyCoreSession.isConnected) {
            SourceHealth.Ok("Spotify conectado via API oficial")
        } else {
            SourceHealth.Rejected("Spotify não conectado: abra o login e digite o Client ID")
        }

    override suspend fun search(
        query: String,
        limit: Int,
        waitForAll: Boolean,
        request: StreamRequest?,
    ): List<Song> {
        val catalog = SpotifyCoreSession.catalog ?: return emptyList()
        return runCatching { catalog.search(query, limit) }
            .getOrDefault(emptyList())
            .map { track ->
                val minutes = track.durationMs / 60_000
                val seconds = (track.durationMs / 1000) % 60
                Song(
                    videoId = track.uri,
                    title = track.title,
                    artist = track.artists.joinToString(", "),
                    thumbnailUrl = track.imageUrl,
                    durationText = "%d:%02d".format(minutes, seconds),
                    albumName = track.album,
                )
            }
    }

    override suspend fun stream(trackId: String, request: StreamRequest): SourceStream? = null
}
