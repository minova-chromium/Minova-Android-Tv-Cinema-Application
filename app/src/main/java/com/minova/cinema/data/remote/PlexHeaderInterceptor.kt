package com.minova.cinema.data.remote

import okhttp3.Interceptor
import okhttp3.Response

class PlexHeaderInterceptor(
    private val connection: PlexConnection,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()
        for ((name, value) in PlexConfig.requestHeaders(connection)) {
            requestBuilder.header(name, value)
        }
        return chain.proceed(requestBuilder.build())
    }
}
