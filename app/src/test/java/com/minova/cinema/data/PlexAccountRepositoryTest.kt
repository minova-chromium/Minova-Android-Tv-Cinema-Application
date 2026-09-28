package com.minova.cinema.data

import com.minova.cinema.data.remote.PlexResourceConnectionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlexAccountRepositoryTest {
    @Test
    fun `short pin authorization url opens the prefilled Plex link page`() {
        val url = buildPlexAuthorizationUrl("A1B2")

        assertEquals("https://plex.tv/link/?pin=A1B2", url)
    }

    @Test
    fun `server discovery prefers local then direct remote then relay`() {
        val relay = PlexResourceConnectionDto("https://relay.example", local = false, relay = true)
        val remote = PlexResourceConnectionDto("https://remote.example", local = false, relay = false)
        val localHttp = PlexResourceConnectionDto("http://192.168.1.10:32400", local = true, relay = false)
        val localHttps = PlexResourceConnectionDto("https://local.example:32400", local = true, relay = false)

        assertEquals(
            listOf(localHttps, localHttp, remote, relay),
            rankPlexConnections(listOf(relay, localHttp, remote, localHttps)),
        )
    }

    @Test
    fun `server discovery removes duplicate addresses`() {
        val connection = PlexResourceConnectionDto("https://server.example/", local = true, relay = false)
        val duplicate = connection.copy(uri = "https://server.example")

        assertEquals(1, rankPlexConnections(listOf(connection, duplicate)).size)
    }
}
