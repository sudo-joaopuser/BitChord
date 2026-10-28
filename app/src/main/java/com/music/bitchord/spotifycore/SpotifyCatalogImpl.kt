package com.music.bitchord.spotifycore

import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

class SpotifyCatalogImpl(private val tokens: TokenSource) : SpotifyCatalog {
    private suspend fun get(path: String): JSONObject =
        SpotifyHttp.apiGet(tokens.accessToken(), path)

    override suspend fun profile(): String {
        val json = get("/me")
        return json.optString("display_name").takeIf { it.isNotBlank() }
            ?: json.optString("id")
    }

    override suspend fun playlists(limit: Int): List<SpotifyPlaylist> {
        val items = get("/me/playlists?limit=$limit").optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val o = items.optJSONObject(i) ?: return@mapNotNull null
            SpotifyPlaylist(
                id = o.optString("id"),
                uri = o.optString("uri"),
                name = o.optString("name"),
                owner = o.optJSONObject("owner")?.optString("display_name"),
                imageUrl = firstImage(o.optJSONArray("images")),
                totalTracks = o.optJSONObject("tracks")?.optInt("total", 0) ?: 0,
            )
        }
    }

    override suspend fun playlistTracks(playlistId: String): List<SpotifyTrack> {
        val items = get("/playlists/$playlistId/items?limit=100").optJSONArray("items")
            ?: return emptyList()
        return items.toTracks()
    }

    override suspend fun likedTracks(): List<SpotifyTrack> {
        val items = get("/me/tracks?limit=50").optJSONArray("items") ?: return emptyList()
        return items.toTracks()
    }

    override suspend fun search(query: String, limit: Int): List<SpotifyTrack> {
        val q = URLEncoder.encode(query, "UTF-8")
        val tracks = get("/search?q=$q&type=track&limit=$limit")
            .optJSONObject("tracks")?.optJSONArray("items") ?: return emptyList()
        return (0 until tracks.length()).mapNotNull { i ->
            tracks.optJSONObject(i)?.let { trackFrom(it) }
        }
    }

    override suspend fun albumTracks(albumId: String): List<SpotifyTrack> {
        val items = get("/albums/$albumId/tracks?limit=100").optJSONArray("items")
            ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            items.optJSONObject(i)?.let { trackFrom(it) }
        }
    }

    override suspend fun artistTopTracks(artistId: String): List<SpotifyTrack> {
        val tracks = get("/artists/$artistId/top-tracks?market=BR").optJSONArray("tracks")
            ?: return emptyList()
        return (0 until tracks.length()).mapNotNull { i ->
            tracks.optJSONObject(i)?.let { trackFrom(it) }
        }
    }

    private fun JSONArray.toTracks(): List<SpotifyTrack> =
        (0 until length()).mapNotNull { i ->
            val item = optJSONObject(i) ?: return@mapNotNull null
            val track = item.optJSONObject("track") ?: item
            if (track.optString("id").isBlank()) null else trackFrom(track)
        }

    private fun trackFrom(o: JSONObject): SpotifyTrack {
        val artists = o.optJSONArray("artists")
        val names = (0 until (artists?.length() ?: 0)).mapNotNull { i ->
            artists?.optJSONObject(i)?.optString("name")
        }
        val album = o.optJSONObject("album")
        return SpotifyTrack(
            id = o.optString("id"),
            uri = o.optString("uri"),
            title = o.optString("name"),
            artists = names,
            album = album?.optString("name"),
            durationMs = o.optLong("duration_ms", 0L),
            imageUrl = firstImage(album?.optJSONArray("images")),
        )
    }

    private fun firstImage(images: JSONArray?): String? =
        if (images != null && images.length() > 0) {
            images.optJSONObject(0)?.optString("url")?.takeIf { it.isNotBlank() }
        } else null
}

class SpotifyConnectPlayer(private val tokens: TokenSource) : SpotifyPlayer {
    private suspend fun token(): String = tokens.accessToken()

    override suspend fun devices(): List<SpotifyDevice> {
        val arr = SpotifyHttp.apiGet(token(), "/me/player/devices")
            .optJSONArray("devices") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            SpotifyDevice(
                id = o.optString("id").takeIf { it.isNotBlank() },
                name = o.optString("name"),
                type = o.optString("type"),
                isActive = o.optBoolean("is_active", false),
            )
        }
    }

    override suspend fun playbackState(): SpotifyPlaybackState? {
        val (code, text) = SpotifyHttp.request(
            "GET", "https://api.spotify.com/v1/me/player", token()
        )
        if (code == 204 || text.isBlank()) return null
        val json = JSONObject(text)
        val item = json.optJSONObject("item")
        val deviceObj = json.optJSONObject("device")
        val device = deviceObj?.let {
            SpotifyDevice(
                id = it.optString("id").takeIf { s -> s.isNotBlank() },
                name = it.optString("name"),
                type = it.optString("type"),
                isActive = it.optBoolean("is_active", false),
            )
        }
        return SpotifyPlaybackState(
            track = null,
            isPlaying = json.optBoolean("is_playing", false),
            positionMs = json.optLong("progress_ms", 0L),
            device = device,
            shuffle = json.optBoolean("shuffle_state", false),
            repeat = json.optString("repeat_state", "off"),
        ).let { state ->
            if (item == null) state else state.copy(
                track = SpotifyTrack(
                    id = item.optString("id"),
                    uri = item.optString("uri"),
                    title = item.optString("name"),
                    artists = emptyList(),
                    album = null,
                    durationMs = item.optLong("duration_ms", 0L),
                    imageUrl = null,
                )
            )
        }
    }

    override suspend fun play(uris: List<String>, deviceId: String?) {
        val body = JSONObject().apply {
            put("uris", JSONArray().apply { uris.forEach { put(it) } })
        }.toString()
        val path = if (deviceId != null) "/me/player/play?device_id=$deviceId" else "/me/player/play"
        SpotifyHttp.api(token(), "PUT", path, body)
    }

    override suspend fun pause() {
        SpotifyHttp.api(token(), "PUT", "/me/player/pause")
    }

    override suspend fun next() {
        SpotifyHttp.api(token(), "POST", "/me/player/next")
    }

    override suspend fun previous() {
        SpotifyHttp.api(token(), "POST", "/me/player/previous")
    }

    override suspend fun seek(positionMs: Long) {
        SpotifyHttp.api(token(), "PUT", "/me/player/seek?position_ms=$positionMs")
    }

    override suspend fun setShuffle(enabled: Boolean) {
        SpotifyHttp.api(token(), "PUT", "/me/player/shuffle?state=$enabled")
    }

    override suspend fun transfer(deviceId: String) {
        val body = JSONObject().apply {
            put("device_ids", JSONArray().apply { put(deviceId) })
            put("play", false)
        }.toString()
        SpotifyHttp.api(token(), "PUT", "/me/player", body)
    }
}
