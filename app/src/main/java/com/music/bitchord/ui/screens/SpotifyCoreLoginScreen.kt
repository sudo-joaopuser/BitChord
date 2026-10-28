package com.music.bitchord.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.music.bitchord.spotifycore.DEFAULT_SPOTIFY_REDIRECT_URI
import com.music.bitchord.spotifycore.SpotifyLoginFlow
import com.music.bitchord.spotifycore.SpotifyLoginState

/**
 * Spotify login, asked directly in the app — nothing is asked in chat
 * and nothing is hardcoded.
 *
 * The screen asks for the Developer Client ID first, shows the exact
 * redirect URI to register in the Spotify Developer Dashboard, and
 * only then opens Spotify in the browser. Texts are literal Portuguese
 * for this fork; moving them to resources is a cleanup step, not a
 * reason to invent string resources that do not exist yet.
 *
 * [onConnect] receives the validated Client ID, redirect URI and PKCE
 * verifier. The activity stores them, opens the authorize URL in the
 * browser, and exchanges the code when the browser returns to
 * bitchord-spotify://callback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyCoreLoginScreen(
    onNavigateUp: () -> Unit,
    onConnect: (clientId: String, redirectUri: String, verifier: String) -> Unit,
    initialError: String? = null,
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(SpotifyLoginState(error = initialError)) }
    var clientId by remember { mutableStateOf("") }
    var redirectUri by remember { mutableStateOf(DEFAULT_SPOTIFY_REDIRECT_URI) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Entrar com Spotify") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Para tocar com o Spotify, o app usa a sua própria conta de desenvolvedor do Spotify. Nada aqui vai para o chat e nenhum Client ID vem embutido no app.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "1. Abra developer.spotify.com/dashboard e crie um app.\n2. Registre este redirect URI lá, exatamente como está abaixo.\n3. Copie o Client ID do app e cole aqui.\n4. Toque em Conectar com Spotify.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = clientId,
                onValueChange = { clientId = it.trim(); state = state.copy(error = null) },
                label = { Text("Client ID do Spotify Developer") },
                placeholder = { Text("32 caracteres") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = redirectUri,
                onValueChange = { redirectUri = it.trim() },
                label = { Text("Redirect URI (registre este no dashboard)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Redirect URI", redirectUri))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Copiar redirect URI")
            }
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://developer.spotify.com/dashboard"))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Abrir Spotify Developer Dashboard")
            }
            state.error?.let { error ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val next = SpotifyLoginFlow.startLogin(
                        state.copy(clientId = clientId, redirectUri = redirectUri),
                    )
                    state = next
                    val verifier = next.verifier
                    if (next.error == null && verifier != null) {
                        onConnect(clientId.trim(), redirectUri.trim(), verifier)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Conectar com Spotify")
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Só conta Premium toca faixa avulsa. Conta free é barrada pelo próprio Spotify.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
