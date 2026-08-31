package com.minova.cinema.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Header

interface PlexHomeApiService {
    @GET("api/v2/home/users")
    suspend fun getHomeUsers(): List<PlexHomeUserDto>

    @POST("api/v2/home/users/{uuid}/switch")
    suspend fun switchUser(
        @Path("uuid") uuid: String,
        @Query("pin") pin: String? = null,
    ): PlexHomeSwitchResponse

    @GET("api/v2/resources")
    suspend fun getResources(
        @Query("includeHttps") includeHttps: Int = 1,
        @Query("includeRelay") includeRelay: Int = 1,
        @Header("X-Plex-Token") switchedToken: String,
    ): List<PlexResourceDto>
}

data class PlexHomeUserDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("uuid") val uuid: String = "",
    @SerializedName("title") val title: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("thumb") val thumb: String? = null,
    @SerializedName("protected") val protected: Boolean = false,
    @SerializedName("restricted") val restricted: Boolean = false,
    @SerializedName("admin") val admin: Boolean = false,
)

data class PlexHomeSwitchResponse(
    @SerializedName("authToken") val authToken: String? = null,
    @SerializedName("authentication_token") val authenticationToken: String? = null,
) {
    val token: String? get() = authToken ?: authenticationToken
}

data class PlexResourceDto(
    @SerializedName("name") val name: String? = null,
    @SerializedName("provides") val provides: String? = null,
    @SerializedName("accessToken") val accessToken: String? = null,
    @SerializedName("clientIdentifier") val clientIdentifier: String? = null,
    @SerializedName("connections") val connections: List<PlexResourceConnectionDto> = emptyList(),
)

data class PlexResourceConnectionDto(
    @SerializedName("uri") val uri: String? = null,
    @SerializedName("local") val local: Boolean? = null,
    @SerializedName("relay") val relay: Boolean? = null,
)
