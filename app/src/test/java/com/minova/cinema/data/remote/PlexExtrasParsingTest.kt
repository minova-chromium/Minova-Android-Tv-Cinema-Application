package com.minova.cinema.data.remote

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class PlexExtrasParsingTest {
    @Test
    fun metadataIncludeExtrasResponseExposesTrailer() {
        val response = Gson().fromJson(
            """
            {
              "MediaContainer": {
                "Metadata": [{
                  "ratingKey": "movie-1",
                  "type": "movie",
                  "title": "Feature",
                  "Extras": {
                    "Metadata": [{
                      "ratingKey": "trailer-1",
                      "type": "clip",
                      "subtype": "trailer",
                      "extraType": 1,
                      "title": "Official Trailer"
                    }]
                  }
                }]
              }
            }
            """.trimIndent(),
            PlexLibraryResponse::class.java,
        )

        val trailer = response.mediaContainer.metadata.single().extras!!.metadata.single()
        assertEquals("trailer", trailer.subtype)
        assertEquals(1, trailer.extraType)
    }
}
