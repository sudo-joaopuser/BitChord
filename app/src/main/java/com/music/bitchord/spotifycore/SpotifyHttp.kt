package com.music.bitchord.spotifycore

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Small HTTP helper for the official Spotify APIs. No other client needed. */
internal object SpotifyHttp {
    fun form(params: Map<String, String>): String =
        params.entries.joinToString("&") { (k, v) ->
            URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
        }

    // Blocking HttpURLConnection must never run on the caller's thread:
    // the library/search screens call the catalog from LaunchedEffect and
    // rememberCoroutineScope (Main), and Android throws
    // NetworkOnMainThreadException there — an exception with a null
    // message, which the old error formatting rendered as a generic
    // "Falha ao falar com o Spotify" and the old library code rendered
    // as an empty page (error = it.message = null). Every Spotify call
    // goes through here, so switching to IO here fixes every call site.
    suspend fun request(
        method: String,
        url: String,
        token: String? = null,
        body: String? = null,
        contentType: String? = null,
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            // No timeout used to mean a hung call never became an error:
            // the library page sat on its initial empty state forever,
            // with no profile, no error and no spinner to explain it.
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.requestMethod = method
            conn.setRequestProperty("Accept", "application/json")
            if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType ?: "application/json")
                conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let {
                BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).use { r -> r.readText() }
            }.orEmpty()
            if (code == 403) throw SpotifyPremiumRequiredException()
            if (code !in 200..299) throw SpotifyApiException(code, text.take(500))
            code to text
        } finally {
            conn.disconnect()
        }
    }

    suspend fun apiGet(token: String, path: String): JSONObject {
        val (_, text) = request("GET", "https://api.spotify.com/v1$path", token)
        return JSONObject(text)
    }

    suspend fun api(token: String, method: String, path: String, body: String? = null): String {
        val (_, text) = request(method, "https://api.spotify.com/v1$path", token, body)
        return text
    }

    suspend fun tokenRequest(body: String): JSONObject {
        val (_, text) = request(
            "POST", "https://accounts.spotify.com/api/token",
            body = body, contentType = "application/x-www-form-urlencoded",
        )
        return JSONObject(text)
    }
}
