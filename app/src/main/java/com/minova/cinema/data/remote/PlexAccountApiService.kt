package com.minova.cinema.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface PlexAccountApiService {
    @POST("api/v2/pins")
    suspend fun createPin(
        @Query("strong") strong: Boolean = false,
    ): PlexPinResponse

    @GET("api/v2/pins/{pinId}")
    suspend fun getPin(
        @Path("pinId") pinId: Long,
    ): PlexPinResponse

    @GET("api/v2/resources")
    suspend fun getResources(
        @Header("X-Plex-Token") accountToken: String,
        @Query("includeHttps") includeHttps: Int = 1,
        @Query("includeRelay") includeRelay: Int = 1,
    ): List<PlexResourceDto>
}

data class PlexPinResponse(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("code") val code: String = "",
    @SerializedName("authToken") val authToken: String? = null,
)
