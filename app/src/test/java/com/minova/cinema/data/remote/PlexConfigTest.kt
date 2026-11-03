package com.minova.cinema.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class PlexConfigTest {
    @Test
    fun dolbyVisionCompatibilityStreamForcesVideoTranscode() {
        assertEquals("0", plexDirectStreamFlag(forceVideoTranscode = true))
        assertEquals("1", plexDirectStreamFlag(forceVideoTranscode = false))
    }

    @Test
    fun hybridDolbyVisionProfilePreservesOriginalHevcInMpegTs() {
        assertEquals(
            "add-transcode-target(type=videoProfile&context=streaming&protocol=hls&" +
                "container=mpegts&videoCodec=hevc&audioCodec=ac3,eac3,dca,aac&" +
                "subtitleCodec=webvtt&replace=true)",
            ORIGINAL_DOLBY_VISION_HLS_PROFILE,
        )
    }

    @Test
    fun bareLanAddressUsesPlexPort() {
        assertEquals(
            "http://192.168.1.20:32400/",
            PlexConfig.normalizeServerAddress("192.168.1.20"),
        )
    }

    @Test
    fun explicitHttpWithoutPortUsesPlexPort() {
        assertEquals(
            "http://plex.local:32400/",
            PlexConfig.normalizeServerAddress("http://plex.local"),
        )
    }

    @Test
    fun explicitHttpsWithoutPortKeepsStandardTlsPort() {
        assertEquals(
            "https://plex.example.ts.net/",
            PlexConfig.normalizeServerAddress("https://plex.example.ts.net"),
        )
    }

    @Test
    fun explicitHttpsPortIsPreserved() {
        assertEquals(
            "https://plex.example.ts.net:32400/",
            PlexConfig.normalizeServerAddress("https://plex.example.ts.net:32400"),
        )
    }
}
