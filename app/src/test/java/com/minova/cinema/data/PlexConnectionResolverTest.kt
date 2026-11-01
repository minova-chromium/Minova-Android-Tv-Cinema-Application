package com.minova.cinema.data

import com.minova.cinema.data.remote.PlexConnection
import java.net.InetAddress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlexConnectionResolverTest {
    @Test
    fun `prefers a discovered wired LAN server over an online route`() = runBlocking {
        val remote = "https://plexpc.example.ts.net/"
        val local = "http://192.168.50.25:32400/"
        val attempted = mutableListOf<String>()
        val resolver = PlexConnectionResolver(
            discoverLan = { listOf(PlexLanServer(local, "Home Plex", "server-id")) },
            canConnect = { attempted += it.baseUrl; it.baseUrl == local },
        )
        val resolved = resolver.resolve(PlexConnection(remote, "token"), serverIdentifier = "server-id")
        assertEquals(local, resolved.baseUrl)
        assertTrue(remote in resolved.fallbackBaseUrls)
        assertEquals(listOf(local), attempted)
    }

    @Test
    fun `uses a saved LAN route without waiting for discovery`() = runBlocking {
        var discoveries = 0
        val local = "http://192.168.1.12:32400/"
        val resolver = PlexConnectionResolver(
            discoverLan = { discoveries++; emptyList() },
            canConnect = { it.baseUrl == local },
        )
        val resolved = resolver.resolve(
            PlexConnection("https://remote.example/", "token", fallbackBaseUrls = listOf(local)),
        )
        assertEquals(local, resolved.baseUrl)
        assertEquals(0, discoveries)
    }

    @Test
    fun `ignores a different Plex server discovered on the same router`() = runBlocking {
        val remote = "https://plexpc.example.ts.net/"
        val wrongLocal = "http://192.168.50.99:32400/"
        val resolver = PlexConnectionResolver(
            discoverLan = { listOf(PlexLanServer(wrongLocal, "Neighbour", "other-server")) },
            canConnect = { it.baseUrl == remote },
        )

        val resolved = resolver.resolve(PlexConnection(remote, "token"), serverIdentifier = "my-server")
        assertEquals(remote, resolved.baseUrl)
        assertFalse(wrongLocal in resolved.fallbackBaseUrls)
    }

    @Test
    fun `derives a direct IP route from plex direct`() {
        assertEquals(
            "http://192.168.10.7:32400/",
            plexDirectLanFallback("https://192-168-10-7.abcdef.plex.direct:32400/"),
        )
        assertTrue(isDirectLanPlexAddress("http://10.0.0.4:32400/"))
        assertFalse(isDirectLanPlexAddress("https://example.com/"))
    }

    @Test
    fun `parses a Plex GDM reply from a private network address`() {
        val reply = "HTTP/1.0 200 OK\r\nName: Wired Plex\r\nPort: 32400\r\nResource-Identifier: server-id\r\n\r\n"
        val parsed = parseGdmResponse(reply.toByteArray(), InetAddress.getByName("192.168.50.25"))
        assertEquals("http://192.168.50.25:32400/", parsed?.baseUrl)
        assertEquals("Wired Plex", parsed?.name)
        assertEquals("server-id", parsed?.identifier)
        assertEquals(null, parseGdmResponse(reply.toByteArray(), InetAddress.getByName("203.0.113.1")))
    }
}
