package com.music.bitchord.playback


/**
 * The one-way escape from a higher-quality source that failed after it had
 * already been selected.
 *
 * A normal retry is deliberately free to resolve again, which is exactly
 * wrong for an addon/JioSaavn failure: the same deterministic catalogue
 * lookup wins again and hands the player the same broken stream.
 */
internal object PlaybackFallback {

    /** Whether [uri] is currently allowed to resolve to something above the pinned source. */
    fun isAlternative(uri: String, substitutedYouTube: Boolean): Boolean {
        if (parameter(uri, DIRECT_YOUTUBE_PARAMETER) == "1") return false
        if (uri.substringBefore('?').substringBefore('#') == "bitchord://source") return true
        val rendition = parameter(uri, QualityUpgrade.MARKER)
        return substitutedYouTube || rendition == "hifi" || rendition?.startsWith("hifi-") == true
    }

    private fun parameter(uri: String, wanted: String): String? {
        val query = uri.substringAfter('?', missingDelimiterValue = "").substringBefore('#')
        return query.split('&')
            .firstOrNull { it.substringBefore('=') == wanted }
            ?.substringAfter('=', missingDelimiterValue = "")
    }

}
