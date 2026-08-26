package com.minova.cinema.data.remote

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetSocketAddress

class PlexApiServiceTest {
    @Test
    fun guidBatchKeepsLiteralCommaBetweenEncodedGuids() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var requestedPath: String? = null
        server.createContext("/") { exchange ->
            requestedPath = exchange.requestURI.rawPath
            val response = """{"MediaContainer":{"size":0,"Metadata":[]}}"""
                .toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()

        try {
            val api = PlexServiceFactory.create(
                PlexConnection(
                    baseUrl = "http://127.0.0.1:${server.address.port}/",
                    token = "test-token",
                ),
            )

            api.resolveMetadataGuids(
                "plex%3A%2F%2Fmovie%2Ffirst,plex%3A%2F%2Fshow%2Fsecond",
            )

            assertEquals(
                "/library/metadata/" +
                    "plex%3A%2F%2Fmovie%2Ffirst,plex%3A%2F%2Fshow%2Fsecond",
                requestedPath,
            )
        } finally {
            server.stop(0)
        }
    }
}
