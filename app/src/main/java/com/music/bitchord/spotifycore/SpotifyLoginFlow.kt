package com.music.bitchord.spotifycore

/**
 * Login screen logic for the clean core.
 *
 * The screen MUST ask for the Developer Client ID first: it is the
 * user's own Spotify Developer app, it is not hardcoded and it is not
 * a secret, so it is stored in plain prefs and shown on screen. The
 * screen also shows the redirect URI, because the user has to register
 * that exact URI in the Spotify Developer Dashboard before login works.
 * The Compose UI is a thin layer over this state: field for Client ID,
 * read-only redirect URI with copy, button to open Spotify, error text.
 */
const val DEFAULT_SPOTIFY_REDIRECT_URI = "bitchord-spotify://callback"

data class SpotifyLoginState(
    val clientId: String = "",
    val redirectUri: String = DEFAULT_SPOTIFY_REDIRECT_URI,
    val verifier: String? = null,
    val authorizeUrl: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val signedIn: Boolean = false,
)

object SpotifyLoginFlow {
    private val clientIdPattern = Regex("[A-Za-z0-9]{32}")

    fun isValidClientId(clientId: String): Boolean =
        clientIdPattern.matches(clientId.trim())

    /** Step 1: validate the typed Client ID, build the Spotify URL to open. */
    fun startLogin(state: SpotifyLoginState, auth: SpotifyAuth): SpotifyLoginState {
        val clientId = state.clientId.trim()
        if (!isValidClientId(clientId)) {
            return state.copy(
                error = "Client ID inválido: são 32 caracteres, copiados do Spotify Developer Dashboard."
            )
        }
        val verifier = SpotifyPkce.codeVerifier()
        return state.copy(
            clientId = clientId,
            verifier = verifier,
            authorizeUrl = auth.authorizeUrl(clientId, state.redirectUri, verifier),
            error = null,
        )
    }

    /** Step 2: Spotify redirected back with a code, exchange it for tokens. */
    suspend fun finishLogin(
        state: SpotifyLoginState, code: String, auth: SpotifyAuth,
    ): SpotifyLoginState {
        val verifier = state.verifier
            ?: return state.copy(error = "Login não iniciado: abra o Spotify de novo.")
        return try {
            auth.exchangeCode(code, state.clientId.trim(), state.redirectUri, verifier)
            state.copy(isLoading = false, signedIn = true, error = null)
        } catch (e: Exception) {
            state.copy(isLoading = false, error = e.message ?: "Falha no login")
        }
    }
}
