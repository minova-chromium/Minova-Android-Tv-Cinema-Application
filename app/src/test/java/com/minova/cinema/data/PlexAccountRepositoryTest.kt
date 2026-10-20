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

    @Test
    fun `reconnect selects the saved Plex server id after its address changes`() {
        val oldServer = server("old", "Old server", "http://old-server.example:32400")
        val selectedServer = server("selected", "Living room", "http://living-room.example:32400")

        assertEquals(
            selectedServer,
            selectReconnectServer(
                servers = listOf(oldServer, selectedServer),
                serverId = "selected",
                serverName = "Living room",
                currentBaseUrl = "http://previous-address.example:32400",
            ),
        )
    }

    @Test
    fun `reconnect identifies an existing install by its previously saved address`() {
        val selectedServer = server("selected", "Living room", "https://plex.example:32400/")
        val otherServer = server("other", "Other", "http://other.example:32400")

        assertEquals(
            selectedServer,
            selectReconnectServer(
                servers = listOf(otherServer, selectedServer),
                serverId = null,
                serverName = null,
                currentBaseUrl = "https://PLEX.example:32400",
            ),
        )
    }

    @Test
    fun `reconnect safely falls back when the account has one server`() {
        val onlyServer = server("only", "Home", "https://home.example")

        assertEquals(
            onlyServer,
            selectReconnectServer(
                servers = listOf(onlyServer),
                serverId = null,
                serverName = null,
                currentBaseUrl = "http://unavailable.local:32400",
            ),
        )
    }

    @Test
    fun `reconnect does not silently switch among ambiguous servers`() {
        val servers = listOf(
            server("one", "One", "https://one.example"),
            server("two", "Two", "https://two.example"),
        )

        assertEquals(
            null,
            selectReconnectServer(
                servers = servers,
                serverId = null,
                serverName = null,
                currentBaseUrl = "http://unavailable.local:32400",
            ),
        )
    }

    private fun server(id: String, name: String, address: String) = PlexDiscoveredServer(
        id = id,
        name = name,
        owned = true,
        accountToken = "account-token",
        accessToken = "server-token",
        connections = listOf(
            PlexResourceConnectionDto(address, local = true, relay = false),
        ),
    )
}
