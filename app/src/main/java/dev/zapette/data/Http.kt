package dev.zapette.data

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object Http {
    const val DEFAULT_USER_AGENT = "Zapette/1.0 (Linux; Android TV)"

    @Volatile
    var userAgent: String = DEFAULT_USER_AGENT

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", userAgent)
                        .build()
                )
            }
            .build()
    }
}
