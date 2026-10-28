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
import com.music.bitchord.R
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.spotify_logo
import com.music.bitchord.spotifycore.SpotifyApiException
import com.music.bitchord.spotifycore.SpotifyCoreSession
import com.music.bitchord.spotifycore.SpotifyPlaylist
import com.music.bitchord.spotifycore.SpotifyTrack
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/**
 * The clean-core Spotify library: playlists and Liked Songs from the
 * official Web API, loaded after sign-in. Tapping a track asks the
 * Connect player to play it on an active Spotify device; with no
 * device active the error is shown instead of a silent miss.
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
    var profileName by remember { mutableStateOf<String?>(null) }
    var profileId by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var catalogMissing by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableStateOf(0) }
    var expandedPlaylistId by remember { mutableStateOf<String?>(null) }
    var expandedTracks by remember { mutableStateOf<List<SpotifyTrack>>(emptyList()) }
    var playError by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SpotifyTrack>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(connected, loadAttempt) {
        if (!connected) {
            playlists = emptyList()
            liked = emptyList()
            topTracks = emptyList()
            profileName = null
            profileId = null
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
        runCatching {
            val info = catalog.profileInfo()
            profileName = info.name
            profileId = info.id
            playlists = catalog.playlists()
            liked = catalog.likedTracks()
            // Fallback content: a library with no playlists and no Liked
            // Songs still has top tracks to show and play.
            topTracks = if (playlists.isEmpty() && liked.isEmpty()) {
                catalog.topTracks()
            } else {
                emptyList()
            }
        }.onFailure { error = it.message }
        loading = false
    }

    fun play(track: SpotifyTrack) {
        val player = SpotifyCoreSession.player
        if (player == null) {
            playError = "Spotify is not connected"
            return
        }
        scope.launch {
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
            }.onSuccess { playError = null }
                .onFailure { failure ->
                    playError = if (failure is SpotifyApiException && failure.status == 404) {
                        "NO_ACTIVE_DEVICE"
                    } else {
                        failure.message ?: "Could not start playback"
                    }
                }
        }
    }

    fun search() {
        val catalog = SpotifyCoreSession.catalog ?: return
        val query = searchQuery.trim()
        if (query.isBlank()) return
        searching = true
        searchError = null
        scope.launch {
            runCatching { catalog.search(query, limit = 20) }
                .onSuccess { searchResults = it }
                .onFailure { searchError = it.message }
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
                    Text(
                        text = stringResource(
                            R.string.spotify_library_counts,
                            playlists.size,
                            liked.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
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
                                        .onFailure { error = it.message }
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
            if (!loading && error == null && !catalogMissing && playlists.isEmpty() && liked.isEmpty() && topTracks.isEmpty()) {
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
