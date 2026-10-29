package com.music.bitchord.spotifycore

import android.content.Context
import com.google.protobuf.ByteString
import com.spotify.connectstate.Connect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.gianlu.librespot.audio.MetadataWrapper
import xyz.gianlu.librespot.core.Session
import xyz.gianlu.librespot.metadata.PlayableId
import xyz.gianlu.librespot.player.Player
import xyz.gianlu.librespot.player.PlayerConfiguration
import java.security.SecureRandom

/**
 * Spotify playback inside this app, without the official Spotify app.
 *
 * This is librespot-java (the same approach as the librespot-based
 * Linux clients: ncspot, psst, spotify-player), NOT the official Web
 * API — the Web API has no audio stream to hand out, so "play inside
 * the app" is only possible over Spotify's internal protocol. The
 * session opens with the OAuth access token the clean core already
 * holds (type AUTHENTICATION_SPOTIFY_TOKEN, the same credential
 * librespot-java's own OAuth helper builds), so there is no second
 * login — but the token must carry the `streaming` scope, which is
 * why SPOTIFY_SCOPES includes it. A token granted before that scope
 * was added fails here with a real error and the caller falls back
 * to Connect; re-login then grants the scope.
 *
 * Audio goes to [AudioTrackSinkOutput] (Android AudioTrack), never to
 * Java Sound. The session opens lazily on the first play, on IO, and
 * is reused while the token is unchanged; a changed token closes the
 * old session first. Playback state is exposed as flows so the
 * Spotify surfaces can show what is playing and pause it.
 */
object SpotifyDirectPlayer {
    private const val PREFS = "spotify_direct"
    private const val KEY_DEVICE_ID = "device_id"

    data class NowPlaying(
        val uri: String,
        val title: String?,
        val artist: String?,
        val isPlaying: Boolean,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    @Volatile private var session: Session? = null
    @Volatile private var player: Player? = null
    @Volatile private var sessionToken: String? = null

    /** True once a direct track has been started and not closed. */
    val isActive: Boolean get() = _nowPlaying.value != null

    private val listener = object : Player.EventsListener {
        override fun onContextChanged(player: Player, newUri: String) {}
        override fun onTrackChanged(
            player: Player, id: PlayableId, metadata: MetadataWrapper?, userInitiated: Boolean,
        ) {}
        override fun onPlaybackEnded(player: Player) {
            _nowPlaying.value = _nowPlaying.value?.copy(isPlaying = false)
        }
        override fun onPlaybackPaused(player: Player, trackTime: Long) {
            _nowPlaying.value = _nowPlaying.value?.copy(isPlaying = false)
        }
        override fun onPlaybackResumed(player: Player, trackTime: Long) {
            _nowPlaying.value = _nowPlaying.value?.copy(isPlaying = true)
        }
        override fun onPlaybackFailed(player: Player, e: Exception) {
            _lastError.value = formatSpotifyError(e)
            _nowPlaying.value = _nowPlaying.value?.copy(isPlaying = false)
        }
        override fun onTrackSeeked(player: Player, trackTime: Long) {}
        override fun onMetadataAvailable(player: Player, metadata: MetadataWrapper) {}
        override fun onPlaybackHaltStateChanged(player: Player, halted: Boolean, trackTime: Long) {}
        override fun onInactiveSession(player: Player, timeout: Boolean) {}
        override fun onVolumeChanged(player: Player, volume: Float) {}
        override fun onPanicState(player: Player) {
            _lastError.value = "Sessão direta do Spotify interrompida"
            _nowPlaying.value = _nowPlaying.value?.copy(isPlaying = false)
        }
        override fun onStartedLoading(player: Player) {}
        override fun onFinishedLoading(player: Player) {}
    }

    /**
     * Plays a Spotify track URI inside this app. Throws when the
     * direct session cannot be opened (no token, auth rejected, no
     * streaming scope, network) so the caller can fall back to
     * Connect; a track that fails after loading surfaces through
     * [lastError] instead, because librespot reports it async.
     */
    suspend fun play(context: Context, uri: String, title: String?, artist: String?) {
        // A failed open used to throw without touching lastError, so
        // the only trace was the caller's log line: the user saw the
        // Connect fallback's "open the official app" guidance with no
        // hint the direct player had failed at all. Record the reason
        // first, then rethrow so the caller can still fall back.
        try {
            withContext(Dispatchers.IO) {
                val auth = SpotifyCoreSession.auth
                    ?: throw SpotifyApiException(401, "Not signed in")
                val token = auth.accessToken()
                ensureSession(context, token)
                val p = player ?: throw SpotifyApiException(0, "Player direto indisponível")
                _lastError.value = null
                _nowPlaying.value = NowPlaying(uri, title, artist, true)
                p.load(uri, true, false)
            }
        } catch (e: Exception) {
            _lastError.value = formatSpotifyError(e)
            throw e
        }
    }

    /** Toggles pause/resume of the direct playback, if any. */
    fun playPause() {
        val p = player ?: return
        val current = _nowPlaying.value ?: return
        scope.launch {
            runCatching {
                if (current.isPlaying) p.pause() else p.play()
            }
            // The events listener corrects the state; a failed toggle
            // leaves the previous state rather than flipping it blind.
            if (_nowPlaying.value?.isPlaying == current.isPlaying) {
                _nowPlaying.value = current.copy(isPlaying = !current.isPlaying)
            }
        }
    }

    /** Pauses direct playback, if any. Used when another source takes over. */
    fun pause() {
        player ?: return
        scope.launch { runCatching { player?.pause() } }
    }

    /** Closes the direct session and player. Safe to call repeatedly. */
    suspend fun close() {
        withContext(Dispatchers.IO) {
            runCatching { player?.close() }
            runCatching { session?.close() }
            player = null
            session = null
            sessionToken = null
            _nowPlaying.value = null
        }
    }

    private fun ensureSession(context: Context, token: String) {
        if (session != null && sessionToken == token && player != null) return
        runCatching { player?.close() }
        runCatching { session?.close() }
        player = null
        session = null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (deviceId.isNullOrBlank()) {
            // Session.Builder requires exactly 40 hex chars; a fresh
            // id on every launch would also forget the device, so it
            // is generated once and kept.
            val bytes = ByteArray(20)
            SecureRandom().nextBytes(bytes)
            deviceId = bytes.joinToString("") { "%02x".format(it) }
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        val credentials = com.spotify.Authentication.LoginCredentials.newBuilder()
            .setTyp(com.spotify.Authentication.AuthenticationType.AUTHENTICATION_SPOTIFY_TOKEN)
            .setAuthData(ByteString.copyFromUtf8(token))
            .build()
        val newSession = Session.Builder()
            .credentials(credentials)
            .setDeviceName("BitChord")
            .setDeviceId(deviceId)
            .setDeviceType(Connect.DeviceType.SMARTPHONE)
            .create()
        val conf = PlayerConfiguration.Builder()
            .setOutput(PlayerConfiguration.AudioOutput.CUSTOM)
            .setOutputClass(AudioTrackSinkOutput::class.java.name)
            .build()
        val newPlayer = Player(conf, newSession)
        newPlayer.addEventsListener(listener)
        session = newSession
        player = newPlayer
        sessionToken = token
    }
}
