package com.minova.cinema.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Query

/** Plex account Watchlist API hosted by the Discover provider. */
interface PlexWatchlistApiService {
    @GET("library/sections/watchlist/all")
    suspend fun getWatchlist(
        @Query("includeCollections") includeCollections: Int = 1,
        @Query("includeExternalMedia") includeExternalMedia: Int = 1,
        @Query("X-Plex-Container-Start") start: Int = 0,
        @Query("X-Plex-Container-Size") size: Int = 1_000,
    ): PlexLibraryResponse

    @PUT("actions/addToWatchlist")
    suspend fun addToWatchlist(@Query("ratingKey") providerRatingKey: String): Response<Unit>

    @PUT("actions/removeFromWatchlist")
    suspend fun removeFromWatchlist(@Query("ratingKey") providerRatingKey: String): Response<Unit>
}
