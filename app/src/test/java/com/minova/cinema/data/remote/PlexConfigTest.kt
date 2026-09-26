package com.minova.cinema.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class PlexConfigTest {
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
