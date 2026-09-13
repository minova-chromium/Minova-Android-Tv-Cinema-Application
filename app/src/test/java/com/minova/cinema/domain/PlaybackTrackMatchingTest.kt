package com.minova.cinema.domain

import org.junit.Assert.*
import org.junit.Test

class PlaybackTrackMatchingTest {
    private val main = AudioStream(1, "English main", "eng", "aac", 2, true)
    private val commentary = main.copy(id = 2, label = "Director commentary", selected = false)
    private val english = SubtitleStream(10, "English", "eng", null, "srt", true, false)
    private val sdh = english.copy(id = 11, label = "English SDH", selected = false)

    @Test fun exactAudioLabelTakesPriorityOverSharedLanguageAndCodec() {
        assertEquals(commentary, matchPlexAudio(listOf(main, commentary), "Director commentary", "en", "aac", 2))
        assertNull(matchPlexAudio(listOf(main, commentary), "Audio 2", "en", "aac", 2))
    }

    @Test fun audioFallbackRequiresUniqueMatchingChannels() {
        val surround = main.copy(id = 3, label = "English surround", channels = 6)
        assertEquals(surround, matchPlexAudio(listOf(main, surround), "Audio 3", "en", "aac", 6))
    }

    @Test fun subtitleIdAndLabelKeepSdhSeparateFromOtherEnglishSubtitles() {
        assertEquals(sdh, matchPlexSubtitle(listOf(english, sdh), "plex-subtitle:11", "English", "en"))
        assertEquals(sdh, matchPlexSubtitle(listOf(english, sdh), null, "English SDH", "en"))
        assertNull(matchPlexSubtitle(listOf(english, sdh), null, "Subtitle 2", "en"))
    }

    @Test fun unknownSubtitleDoesNotResolveToAnotherLanguage() {
        assertNull(matchPlexSubtitle(listOf(english), null, "Subtitle 1", "nl"))
        assertEquals(english, matchPlexSubtitle(listOf(english), null, "Subtitle 1", "en"))
    }
}
