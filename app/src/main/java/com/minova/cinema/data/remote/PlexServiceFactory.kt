package com.minova.cinema.data.remote

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object PlexServiceFactory {
    fun createAccount(clientIdentifier: String): PlexAccountApiService {
        val identityHeaders = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header(PlexConfig.HEADER_ACCEPT, "application/json")
                .header(PlexConfig.HEADER_CLIENT_ID, clientIdentifier)
                .header("X-Plex-Product", "Minova Cinema")
                .header("X-Plex-Version", com.minova.cinema.BuildConfig.VERSION_NAME)
                .header("X-Plex-Platform", "Android")
                .header("X-Plex-Device", "Android")
                .header("X-Plex-Device-Name", "Minova Cinema")
                .build()
            chain.proceed(request)
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(identityHeaders)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl("https://plex.tv/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()
            .create(PlexAccountApiService::class.java)
    }

    fun create(connection: PlexConnection): PlexApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(PlexHeaderInterceptor(connection))
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(connection.baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()
            .create(PlexApiService::class.java)
    }

    fun createWatchlist(connection: PlexConnection): PlexWatchlistApiService {
        val client = OkHttpClient.Builder()
            .addInterceptor(PlexHeaderInterceptor(connection))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl("https://discover.provider.plex.tv/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()
            .create(PlexWatchlistApiService::class.java)
    }

    fun createHome(connection: PlexConnection): PlexHomeApiService {
        val client = OkHttpClient.Builder()
            .addInterceptor(PlexHeaderInterceptor(connection))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl("https://plex.tv/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()
            .create(PlexHomeApiService::class.java)
    }
}
