package com.minova.cinema.data.remote

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlexExtrasParsingTest {
    @Test
    fun playbackMetadataExposesDolbyVisionProfile() {
        val response = Gson().fromJson(
            """
            {"MediaContainer":{"Metadata":[{"ratingKey":"7","Media":[{
              "Part":[{"id":11,"key":"/library/parts/11/file.mkv","Stream":[{
                "streamType":1,"codec":"hevc","DOVIPresent":1,"DOVIProfile":8
              }]}]
            }]}]}}
            """.trimIndent(),
            PlexLibraryResponse::class.java,
        )

        val video = response.mediaContainer.metadata.single()
            .media.single().parts.single().streams.single()
        assertEquals(1, video.dolbyVisionPresent)
        assertEquals(8, video.dolbyVisionProfile)
        assertTrue(video.codec.equals("hevc", ignoreCase = true))
    }

    @Test
    fun playbackMetadataRecognizesDolbyVisionHdr10Hybrid() {
        val json = """
            {
              "MediaContainer":{"Metadata":[{"Media":[{"Part":[{"Stream":[{
                "streamType":1,
                "codec":"hevc",
                "profile":"main 10",
                "bitDepth":10,
                "displayTitle":"4K DoVi/HDR10 (HEVC Main 10)",
                "colorTrc":"smpte2084",
                "DOVIPresent":1,
                "DOVIProfile":8,
                "DOVIBLCompatID":1
              }]}]}]}]}
            }
        """.trimIndent()

        val response = Gson().fromJson(json, PlexLibraryResponse::class.java)
        val stream = response.mediaContainer.metadata.single().media.single().parts.single().streams.single()

        assertTrue(stream.isDolbyVisionHdr10Hybrid())
        assertEquals(10, stream.bitDepth)
        assertEquals("main 10", stream.profile)
    }

    @Test
    fun regularDolbyVisionDoesNotUseHybridFallback() {
        val regularDolbyVision = Stream(
            streamType = 1,
            codec = "hevc",
            displayTitle = "4K DoVi (HEVC Main 10)",
            profile = "main 10",
            bitDepth = 10,
            dolbyVisionPresent = 1,
            dolbyVisionProfile = 5,
        )
        val hdr10Only = Stream(
            streamType = 1,
            codec = "hevc",
            displayTitle = "4K HDR10 (HEVC Main 10)",
            profile = "main 10",
            bitDepth = 10,
            colorTransfer = "smpte2084",
        )

        assertFalse(regularDolbyVision.isDolbyVisionHdr10Hybrid())
        assertFalse(hdr10Only.isDolbyVisionHdr10Hybrid())
    }

    @Test
    fun playbackMetadataParsesChaptersAndPlexSessionDecisions() {
        val response = Gson().fromJson(
            """
            {"MediaContainer":{"Metadata":[{
              "ratingKey":"7",
              "Chapter":[{"title":"Opening","startTimeOffset":0,"endTimeOffset":60000}],
              "Session":{"id":"session-7"},
              "TranscodeSession":{"videoDecision":"transcode","audioDecision":"copy"}
            }]}}
            """.trimIndent(),
            PlexLibraryResponse::class.java,
        )

        val metadata = response.mediaContainer.metadata.single()
        assertEquals("Opening", metadata.chapters.single().title)
        assertEquals("session-7", metadata.session?.id)
        assertEquals("transcode", metadata.transcodeSession?.videoDecision)
    }

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
