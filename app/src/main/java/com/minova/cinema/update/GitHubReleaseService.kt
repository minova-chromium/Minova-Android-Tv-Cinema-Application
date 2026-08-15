package com.minova.cinema.update

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import com.minova.cinema.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

/** Minimal response required from GitHub's releases/latest endpoint. */
@Keep
data class GitHubReleaseDto(
    @SerializedName("tag_name") val tagName: String,
    val body: String?,
    val assets: List<GitHubReleaseAssetDto> = emptyList(),
)

@Keep
data class GitHubReleaseAssetDto(
    val name: String,
    @SerializedName("browser_download_url") val browserDownloadUrl: String,
)

interface GitHubReleaseService {
    @GET("repos/{owner}/{repository}/releases/latest")
    suspend fun latestRelease(
        @Path("owner") owner: String,
        @Path("repository") repository: String,
    ): GitHubReleaseDto
}

internal object GitHubReleaseServiceFactory {
    fun create(): GitHubReleaseService {
        val client = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .header("User-Agent", "Minova-Cinema-Android-TV")
                        .build(),
                )
            }
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BASIC
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                },
            )
            .build()

        return Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubReleaseService::class.java)
    }
}
