package com.minova.cinema.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.PUT
import retrofit2.http.Query
import retrofit2.http.Url

interface PlexApiService {
    @GET("library/sections")
    suspend fun getLibrarySections(): PlexLibraryResponse

    @GET
    suspend fun getContainer(@Url path: String): PlexLibraryResponse

    @GET("library/metadata/{ratingKey}")
    suspend fun getMetadata(
        @Path("ratingKey") ratingKey: String,
        @Query("includeMarkers") includeMarkers: Int = 1,
    ): PlexLibraryResponse

    @GET("library/metadata/{ratingKey}/children")
    suspend fun getChildren(@Path("ratingKey") ratingKey: String): PlexLibraryResponse

    @GET("library/metadata/{ratingKey}/extras")
    suspend fun getExtras(@Path("ratingKey") ratingKey: String): PlexLibraryResponse

    @GET(":/scrobble")
    suspend fun markWatched(
        @Query("key") ratingKey: String,
        @Query("identifier") identifier: String = "com.plexapp.plugins.library",
    ): Response<Unit>

    @GET(":/unscrobble")
    suspend fun markUnwatched(
        @Query("key") ratingKey: String,
        @Query("identifier") identifier: String = "com.plexapp.plugins.library",
    ): Response<Unit>

    @GET("hubs/continueWatching")
    suspend fun getContinueWatching(): PlexLibraryResponse

    @GET("library/onDeck")
    suspend fun getOnDeck(): PlexLibraryResponse

    @GET(":/timeline")
    suspend fun reportTimeline(
        @Query("ratingKey") ratingKey: String,
        @Query("key") key: String,
        @Query("state") state: String,
        @Query("time") timeMs: Long,
        @Query("duration") durationMs: Long,
    ): Response<Unit>

    @PUT("library/parts/{partId}")
    suspend fun selectSubtitle(
        @Path("partId") partId: Long,
        @Query("subtitleStreamID") subtitleStreamId: Long,
        @Query("allParts") allParts: Int = 1,
    ): Response<Unit>

    @PUT("library/parts/{partId}")
    suspend fun selectAudio(
        @Path("partId") partId: Long,
        @Query("audioStreamID") audioStreamId: Long,
        @Query("allParts") allParts: Int = 1,
    ): Response<Unit>
}
