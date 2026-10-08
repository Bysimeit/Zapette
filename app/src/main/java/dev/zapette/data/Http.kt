package dev.zapette.data

import kotlinx.coroutines.delay
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object Http {
    const val DEFAULT_USER_AGENT = "Zapette/1.0 (Linux; Android TV)"
    private const val RATE_LIMIT_RETRIES = 2

    @Volatile
    var userAgent: String = DEFAULT_USER_AGENT

    @Volatile
    var hostUserAgents: Map<String, String> = emptyMap()

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val request = chain.request()
                chain.proceed(
                    request.newBuilder()
                        .header("User-Agent", hostUserAgents[request.url.host] ?: userAgent)
                        .build()
                )
            }
            .build()
    }

    suspend fun fetch(url: HttpUrl): String {
        var attempt = 0
        while (true) {
            val waitSeconds = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                when {
                    response.isSuccessful -> return response.body?.string().orEmpty()
                    response.code == 429 && attempt < RATE_LIMIT_RETRIES ->
                        response.header("Retry-After")?.toLongOrNull()?.coerceIn(1, 10) ?: (3L shl attempt)
                    else -> throw XtreamException(XtreamException.Reason.HTTP, response.code)
                }
            }
            attempt++
            delay(waitSeconds * 1000)
        }
    }
}
