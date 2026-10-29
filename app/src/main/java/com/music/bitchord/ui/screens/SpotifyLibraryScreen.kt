package com.music.bitchord.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.BuildConfig
import com.music.bitchord.R
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.spotify_logo
import com.music.bitchord.spotifycore.SpotifyAlbum
import com.music.bitchord.spotifycore.SpotifyApiException
import com.music.bitchord.spotifycore.SpotifyCoreSession
import com.music.bitchord.spotifycore.SpotifyDirectPlayer
import com.music.bitchord.spotifycore.formatSpotifyError
import com.music.bitchord.spotifycore.SpotifyPlaylist
import com.music.bitchord.spotifycore.SpotifyTrack
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/**
 * The clean-core Spotify library: playlists and Liked Songs from the
 * official Web API, loaded after sign-in, plus the account's top
 * tracks and new releases. Tapping a track plays it inside this app
 * first (librespot direct player) and falls back to Connect when the
 * direct session cannot open; with no device active the error is
 * shown instead of a silent miss.
 */
@Composable
fun SpotifyLibraryScreen(
    connected: Boolean = SpotifyCoreSession.isConnected,
    onOpenLogin: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var playlists by remember { mutableStateOf<List<SpotifyPlaylist>>(emptyList()) }
    var liked by remember { mutableStateOf<List<SpotifyTrack>>(emptyList()) }
    var topTracks by remember { mutableStateOf<List<SpotifyTrack>>(emptyList()) }
    var newReleases by remember { mutableStateOf<List<SpotifyAlbum>>(emptyList()) }
    var profileName by remember { mutableStateOf<String?>(null) }
    var profileId by remember { mutableStateOf<String?>(null) }
    var profileCountry by remember { mutableStateOf<String?>(null) }
    var profileLoaded by remember { mutableStateOf(false) }
    var playlistTotal by remember { mutableStateOf(-1) }
    var likedTotal by remember { mutableStateOf(-1) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var catalogMissing by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableStateOf(0) }
    var expandedPlaylistId by remember { mutableStateOf<String?>(null) }
    var expandedTracks by remember { mutableStateOf<List<SpotifyTrack>>(emptyList()) }
    var expandedAlbumId by remember { mutableStateOf<String?>(null) }
    var expandedAlbumTracks by remember { mutableStateOf<List<SpotifyTrack>>(emptyList()) }
    var playError by remember { mutableStateOf<String?>(null) }
    var playInfo by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SpotifyTrack>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Direct playback state: the strip below shows what librespot is
    // playing inside this app and pauses/resumes it, since the Media3
    // bottom bar has no item for a direct track.
    val directNowPlaying by SpotifyDirectPlayer.nowPlaying.collectAsState()
    val directError by SpotifyDirectPlayer.lastError.collectAsState()

    LaunchedEffect(connected, loadAttempt) {
        if (!connected) {
            playlists = emptyList()
            liked = emptyList()
            topTracks = emptyList()
            newReleases = emptyList()
            profileName = null
            profileId = null
            profileCountry = null
            profileLoaded = false
            playlistTotal = -1
            likedTotal = -1
            error = null
            catalogMissing = false
            return@LaunchedEffect
        }
        // A connected flag with no catalog used to end in a silent empty
        // page: this early return never set loading, profile or error.
        // Rebuild the catalog from saved tokens first, and say so if
        // there is nothing to rebuild from.
        val catalog = SpotifyCoreSession.catalog
            ?: if (SpotifyCoreSession.restore(context)) SpotifyCoreSession.catalog else null
        if (catalog == null) {
            catalogMissing = true
            loading = false
            return@LaunchedEffect
        }
        catalogMissing = false
        loading = true
        error = null
        profileLoaded = false
        // Step 1: the profile must succeed first, on its own. A profile
        // failure used to be indistinguishable from an empty library:
        // with no timeout, a hung GET /me left the page on its initial
        // state (counts 0/0, no profile id, no error, no spinner), and
        // the empty-library message rendered as if the account were
        // simply empty. Now a profile failure is a real error with
        // retry, and the empty message below is gated on profileLoaded.
        val info = runCatching { catalog.profileInfo() }
            .onFailure { error = formatSpotifyError(it) }
            .getOrNull()
        if (info == null || info.id.isBlank()) {
            if (error == null) error = "Resposta de perfil inválida (sem id)"
            playlists = emptyList()
            liked = emptyList()
            topTracks = emptyList()
            newReleases = emptyList()
            profileName = null
            profileId = null
            profileCountry = null
            loading = false
            return@LaunchedEffect
        }
        profileName = info.name
        profileId = info.id
        profileCountry = info.country
        profileLoaded = true
        // Step 2: lists, only with a proven profile.
        runCatching {
            playlists = catalog.playlists()
            liked = catalog.likedTracks()
            // Top tracks always now, not only when the library is
            // empty: they are the screen's recommendation section.
            topTracks = catalog.topTracks()
        }.onFailure { error = formatSpotifyError(it) }
        // New releases are a bonus section: their failure must not
        // hide the library above, so they load separately and a
        // failure simply leaves the section out.
        runCatching { newReleases = catalog.newReleases() }
        // Totals straight from the API, fetched separately so a totals
        // failure never hides the lists: total > 0 with 0 items means
        // parsing broke, total = 0 means the account really is empty.
        if (error == null) {
            runCatching {
                playlistTotal = catalog.playlistsTotal()
                likedTotal = catalog.likedTotal()
            }.onFailure {
                playlistTotal = -1
                likedTotal = -1
            }
        }
        loading = false
    }

    fun play(track: SpotifyTrack) {
        scope.launch {
            // Direct playback inside this app first (librespot, no
            // official app needed); Connect is the fallback when the
            // direct session cannot open, exactly like the shared
            // playSpotifyUri path in MainActivity. A direct failure
            // used to be swallowed here (only .isSuccess was checked),
            // so the tap silently became a Connect attempt and the user
            // saw just the "open the official app" guidance. Now the
            // real reason stays on screen, and a Connect fallback says
            // so instead of passing for in-app playback.
            val directFailure = runCatching {
                SpotifyDirectPlayer.play(
                    context, track.uri, track.title,
                    track.artists.joinToString(", "),
                )
            }.exceptionOrNull()
            if (directFailure == null) {
                playError = null
                playInfo = null
                return@launch
            }
            playError = context.getString(
                R.string.spotify_direct_failed,
                formatSpotifyError(directFailure),
            )
            playInfo = null
            val player = SpotifyCoreSession.player
            if (player == null) {
                playError = "Spotify is not connected"
                return@launch
            }
            runCatching {
                // Connect needs a device: prefer the active one, else move
                // playback to the first available device, else let the
                // API error surface as guidance instead of silence.
                val devices = player.devices()
                val active = devices.firstOrNull { it.isActive && it.id != null }
                val first = devices.firstOrNull { it.id != null }
                when {
                    active != null -> player.play(listOf(track.uri), active.id)
                    first != null -> {
                        player.transfer(first.id!!)
                        player.play(listOf(track.uri), first.id)
                    }
                    else -> player.play(listOf(track.uri))
                }
            }.onSuccess {
                playInfo = context.getString(R.string.spotify_via_connect)
            }.onFailure { failure ->
                playError = if (failure is SpotifyApiException && failure.status == 404) {
                    "NO_ACTIVE_DEVICE"
                } else {
                    formatSpotifyError(failure)
                }
            }
        }
    }

    fun search() {
        // The old version returned silently with no catalog and no error:
        // a tap that visibly does nothing. Rebuild from saved tokens
        // first, and say so if there is nothing to rebuild from.
        val catalog = SpotifyCoreSession.catalog
            ?: if (SpotifyCoreSession.restore(context)) SpotifyCoreSession.catalog else null
        if (catalog == null) {
            searchError = context.getString(R.string.spotify_catalog_missing)
            searched = true
            return
        }
        val query = searchQuery.trim()
        if (query.isBlank()) return
        searching = true
        searchError = null
        scope.launch {
            runCatching { catalog.search(query, limit = 10, market = profileCountry) }
                .onSuccess { searchResults = it }
                .onFailure { searchError = formatSpotifyError(it) }
            searching = false
            searched = true
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.spotify_logo),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(38.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.spotify),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        if (!connected) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = stringResource(R.string.spotify_connect_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onOpenLogin) {
                        Text(stringResource(R.string.spotify_sign_in))
                    }
                }
            }
        } else {
            if (catalogMissing) {
                item {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Text(
                            text = stringResource(R.string.spotify_catalog_missing),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { loadAttempt++ }) {
                            Text(stringResource(R.string.try_again))
                        }
                    }
                }
            }
            profileName?.let { name ->
                item {
                    val idSuffix = profileId?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                    Text(
                        text = stringResource(R.string.spotify_connected_as, name) + idSuffix,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
            if (!loading && !catalogMissing) {
                item {
                    // Same counts line as before, extended with the raw
                    // API totals and a missing-profile-id flag: this is the
                    // diagnostic that separates parsing bugs from an empty
                    // or wrong account, with no new UI element.
                    val totals = if (playlistTotal >= 0 && likedTotal >= 0) {
                        " · API total: $playlistTotal/$likedTotal"
                    } else {
                        ""
                    }
                    val noId = if (profileId.isNullOrBlank()) " · sem id de perfil" else ""
                    Text(
                        text = stringResource(
                            R.string.spotify_library_counts,
                            playlists.size,
                            liked.size,
                        ) + totals + noId,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
            }
            // The strip used to render only while a direct track was
            // playing, so a session that failed to open left no trace:
            // lastError was set but nothing drew it. An async load
            // failure (lastError with a stale now-playing) and a
            // failed open (lastError, no now-playing) both show now.
            if (directNowPlaying != null || directError != null) {
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = directNowPlaying?.title ?: "Spotify",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!directNowPlaying?.artist.isNullOrBlank()) {
                                Text(
                                    text = directNowPlaying?.artist.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            directError?.let { message ->
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        if (directNowPlaying != null) {
                            Button(onClick = { SpotifyDirectPlayer.playPause() }) {
                                Text(if (directNowPlaying?.isPlaying == true) "Pausar" else "Tocar")
                            }
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text(stringResource(R.string.spotify_search_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { search() }, enabled = !searching) {
                        Text(stringResource(R.string.spotify_search_button))
                    }
                }
            }
            if (searching) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            searchError?.let { message ->
                item {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
            if (searched && !searching && searchError == null && searchResults.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.spotify_search_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
            if (searchResults.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.spotify_search_button),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
                    )
                }
                items(searchResults, key = { it.id }) { track ->
                    LibraryRow(
                        title = track.title,
                        subtitle = track.artists.joinToString(", "),
                        imageUrl = track.imageUrl,
                        onClick = { play(track) },
                    )
                }
            }
            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            error?.let { message ->
                item {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { loadAttempt++ }) {
                            Text(stringResource(R.string.try_again))
                        }
                    }
                }
            }
            playError?.let { message ->
                item {
                    Text(
                        text = if (message == "NO_ACTIVE_DEVICE") {
                            stringResource(R.string.spotify_no_active_device)
                        } else {
                            message
                        },
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
            playInfo?.let { message ->
                item {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
            if (topTracks.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.spotify_top_tracks),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(topTracks, key = { it.id }) { track ->
                    LibraryRow(
                        title = track.title,
                        subtitle = track.artists.joinToString(", "),
                        imageUrl = track.imageUrl,
                        onClick = { play(track) },
                    )
                }
            }
            if (newReleases.isNotEmpty()) {
                item {
                    Text(
                        text = "Novidades",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(newReleases, key = { it.id }) { album ->
                    LibraryRow(
                        title = album.name,
                        subtitle = album.artists,
                        imageUrl = album.imageUrl,
                        onClick = {
                            val catalog = SpotifyCoreSession.catalog
                            if (expandedAlbumId == album.id) {
                                expandedAlbumId = null
                                expandedAlbumTracks = emptyList()
                            } else if (catalog != null) {
                                expandedAlbumId = album.id
                                scope.launch {
                                    runCatching { catalog.albumTracks(album.id) }
                                        .onSuccess { expandedAlbumTracks = it }
                                        .onFailure { error = formatSpotifyError(it) }
                                }
                            }
                        },
                    )
                    if (expandedAlbumId == album.id) {
                        expandedAlbumTracks.forEach { track ->
                            LibraryRow(
                                title = track.title,
                                subtitle = track.artists.joinToString(", "),
                                imageUrl = track.imageUrl,
                                onClick = { play(track) },
                            )
                        }
                    }
                }
            }
            if (liked.isNotEmpty()) {
                item {
                    Text(
                        text = "Liked Songs",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
                    )
                }
                items(liked, key = { it.id }) { track ->
                    LibraryRow(
                        title = track.title,
                        subtitle = track.artists.joinToString(", "),
                        imageUrl = track.imageUrl,
                        onClick = { play(track) },
                    )
                }
            }
            if (playlists.isNotEmpty()) {
                item {
                    Text(
                        text = "Playlists",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(playlists, key = { it.id }) { item ->
                    LibraryRow(
                        title = item.name,
                        subtitle = item.owner.orEmpty(),
                        imageUrl = item.imageUrl,
                        onClick = {
                            val catalog = SpotifyCoreSession.catalog
                            if (expandedPlaylistId == item.id) {
                                expandedPlaylistId = null
                                expandedTracks = emptyList()
                            } else if (catalog != null) {
                                expandedPlaylistId = item.id
                                scope.launch {
                                    runCatching { catalog.playlistTracks(item.id) }
                                        .onSuccess { expandedTracks = it }
                                        .onFailure { error = formatSpotifyError(it) }
                                }
                            }
                        },
                    )
                    if (expandedPlaylistId == item.id) {
                        expandedTracks.forEach { track ->
                            LibraryRow(
                                title = track.title,
                                subtitle = track.artists.joinToString(", "),
                                imageUrl = track.imageUrl,
                                onClick = { play(track) },
                            )
                        }
                    }
                }
            }
            if (!loading && error == null && profileLoaded && !catalogMissing && playlists.isEmpty() && liked.isEmpty() && topTracks.isEmpty() && newReleases.isEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Text(
                            text = stringResource(R.string.spotify_library_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { loadAttempt++ }) {
                            Text(stringResource(R.string.try_again))
                        }
                    }
                }
            }
            // Build mark: proves which commit this APK was built from,
            // so the next device test can settle "was it the new build?"
            // by reading the footer instead of guessing.
            item {
                Text(
                    text = stringResource(R.string.spotify_build, BuildConfig.SPOTIFY_BUILD_MARK),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun LibraryRow(
    title: String,
    subtitle: String,
    imageUrl: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
