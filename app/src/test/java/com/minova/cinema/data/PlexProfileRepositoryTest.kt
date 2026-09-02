package com.minova.cinema.data

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class PlexProfileRepositoryTest {
    @Test
    fun parsesObjectWrappedPlexHomeUsers() {
        val response = JsonParser.parseString(
            """{"users":[{"id":1,"uuid":"owner","title":"Owner","admin":true},{"id":2,"uuid":"kid","title":"Kids","restricted":true}]}""",
        )

        val users = parsePlexHomeUsers(response)

        assertEquals(listOf("owner", "kid"), users.map { it.uuid })
        assertEquals(true, users.first().admin)
        assertEquals(true, users.last().restricted)
    }

    @Test
    fun continuesToParseBareArrayResponses() {
        val response = JsonParser.parseString(
            """[{"id":1,"uuid":"owner","username":"owner"}]""",
        )

        assertEquals("owner", parsePlexHomeUsers(response).single().uuid)
    }
}
