package com.music.bitchord

import com.music.bitchord.playback.PlaybackFallback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackFallbackTest {

    @Test
    fun `failed upgraded YouTube item is an alternative`() {
        assertTrue(
            PlaybackFallback.isAlternative(
                "bitchord://watch?v=5IX-nUxDtJI&n=Bin%20Tere&q=hifi",
                substitutedYouTube = false,
            ),
        )
    }

    @Test
    fun `failed substituted base item is an alternative`() {
        assertTrue(
            PlaybackFallback.isAlternative(
                "bitchord://watch?v=5IX-nUxDtJI&n=Bin%20Tere",
                substitutedYouTube = true,
            ),
        )
    }

    @Test
    fun `direct YouTube fallback never falls back recursively`() {
        assertFalse(
            PlaybackFallback.isAlternative(
                "bitchord://watch?v=5IX-nUxDtJI&direct_youtube=1&q=original",
                substitutedYouTube = true,
            ),
        )
    }

    @Test
    fun `ordinary YouTube audio is not treated as a failed alternative`() {
        assertFalse(
            PlaybackFallback.isAlternative(
                "bitchord://watch?v=5IX-nUxDtJI&n=Bin%20Tere",
                substitutedYouTube = false,
            ),
        )
    }

    @Test
    fun `source already forced to YouTube never falls back recursively`() {
        assertFalse(
            PlaybackFallback.isAlternative(
                "bitchord://source?s=addon-1&t=3919244&n=Bin%20Tere&direct_youtube=1&q=original",
                substitutedYouTube = false,
            ),
        )
    }
}
