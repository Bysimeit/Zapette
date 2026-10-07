package dev.zapette.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class XtreamException(val reason: Reason, val httpCode: Int = 0) : IOException(reason.name) {
    enum class Reason { NOT_XTREAM, AUTH_REJECTED, INVALID_URL, HTTP, BAD_RESPONSE }
}

class XtreamApi(val account: Account) {

    val base: String = normalizeServer(account.server)

    suspend fun accountInfo(): AccountInfo = call(apiUrl(null)) { body ->
        val json = JSONObject(body)
        val ui = json.optJSONObject("user_info")
            ?: throw XtreamException(XtreamException.Reason.NOT_XTREAM)
        if (ui.optInt("auth", 0) != 1) throw XtreamException(XtreamException.Reason.AUTH_REJECTED)
        AccountInfo(
            status = ui.str("status") ?: "?",
            expiresAt = ui.str("exp_date")?.toLongOrNull(),
            maxConnections = ui.str("max_connections"),
            activeConnections = ui.str("active_cons"),
            isTrial = ui.optInt("is_trial", 0) == 1,
        )
    }

    suspend fun categories(kind: Kind): List<Category> {
        val action = when (kind) {
            Kind.LIVE -> "get_live_categories"
            Kind.MOVIE -> "get_vod_categories"
            Kind.SERIES -> "get_series_categories"
        }
        return call(apiUrl(action)) { body ->
            jsonList(body).mapNotNull { o ->
                val id = o.str("category_id") ?: return@mapNotNull null
                Category(id, o.str("category_name") ?: "#$id")
            }
        }
    }

    suspend fun streams(kind: Kind, categoryId: String?): List<Entry> {
        val action = when (kind) {
            Kind.LIVE -> "get_live_streams"
            Kind.MOVIE -> "get_vod_streams"
            Kind.SERIES -> "get_series"
        }
        val params = if (categoryId != null) arrayOf("category_id" to categoryId) else emptyArray()
        return call(apiUrl(action, *params)) { body ->
            jsonList(body).mapNotNull { parseEntry(kind, it) }
        }
    }

    suspend fun seriesInfo(seriesId: Int): SeriesDetail =
        call(apiUrl("get_series_info", "series_id" to seriesId.toString())) { body ->
            val o = JSONObject(body)
            val info = o.optJSONObject("info") ?: JSONObject()
            val seasons = sortedMapOf<Int, MutableList<Episode>>()

            fun add(e: JSONObject?, seasonHint: Int) {
                if (e == null) return
                val id = e.str("id") ?: return
                val season = e.optInt("season", seasonHint)
                val number = e.optInt("episode_num", 0)
                val epInfo = e.optJSONObject("info")
                seasons.getOrPut(season) { mutableListOf() } += Episode(
                    id = id,
                    title = e.str("title") ?: "E$number",
                    season = season,
                    number = number,
                    ext = e.str("container_extension") ?: "mp4",
                    image = epInfo?.str("movie_image"),
                    plot = epInfo?.str("plot"),
                    duration = epInfo?.str("duration"),
                )
            }

            when (val episodes = o.opt("episodes")) {
                is JSONObject -> episodes.keys().forEach { key ->
                    val arr = episodes.optJSONArray(key) ?: return@forEach
                    for (i in 0 until arr.length()) add(arr.optJSONObject(i), key.toIntOrNull() ?: 0)
                }
                is JSONArray -> for (i in 0 until episodes.length()) {
                    when (val x = episodes.opt(i)) {
                        is JSONArray -> for (j in 0 until x.length()) add(x.optJSONObject(j), i + 1)
                        is JSONObject -> add(x, 0)
                    }
                }
            }
            seasons.values.forEach { list -> list.sortBy { it.number } }

            SeriesDetail(
                name = info.str("name") ?: "",
                plot = info.str("plot"),
                cover = info.str("cover"),
                genre = info.str("genre"),
                releaseDate = info.str("releaseDate") ?: info.str("release_date"),
                seasons = seasons,
            )
        }

    suspend fun shortEpg(streamId: Int, limit: Int = 3): List<EpgItem> =
        call(apiUrl("get_short_epg", "stream_id" to streamId.toString(), "limit" to limit.toString())) { body ->
            val arr = JSONObject(body).optJSONArray("epg_listings") ?: return@call emptyList()
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { e ->
                EpgItem(
                    title = decodeBase64(e.optString("title")),
                    start = e.str("start_timestamp")?.toLongOrNull() ?: 0L,
                    end = e.str("stop_timestamp")?.toLongOrNull() ?: 0L,
                )
            }
        }

    fun liveUrl(streamId: Int, format: LiveFormat): String = streamUrl("live", "$streamId.${format.ext}")

    fun movieUrl(streamId: Int, ext: String): String = streamUrl("movie", "$streamId.$ext")

    fun episodeUrl(episodeId: String, ext: String): String = streamUrl("series", "$episodeId.$ext")

    private fun streamUrl(type: String, file: String): String =
        base.toHttpUrl().newBuilder()
            .addPathSegment(type)
            .addPathSegment(account.username)
            .addPathSegment(account.password)
            .addPathSegment(file)
            .build()
            .toString()

    private fun apiUrl(action: String?, vararg params: Pair<String, String>): HttpUrl {
        val builder = ("$base/player_api.php".toHttpUrlOrNull()
            ?: throw XtreamException(XtreamException.Reason.INVALID_URL))
            .newBuilder()
            .addQueryParameter("username", account.username)
            .addQueryParameter("password", account.password)
        if (action != null) builder.addQueryParameter("action", action)
        params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build()
    }

    private suspend fun <T> call(url: HttpUrl, parse: (String) -> T): T = withContext(Dispatchers.IO) {
        val body = Http.client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw XtreamException(XtreamException.Reason.HTTP, response.code)
            }
            response.body?.string().orEmpty()
        }
        parse(body)
    }

    private fun parseEntry(kind: Kind, o: JSONObject): Entry? = when (kind) {
        Kind.LIVE -> o.optInt("stream_id", -1).takeIf { it >= 0 }?.let { id ->
            Entry(
                kind = kind, id = id,
                name = o.str("name") ?: "#$id",
                image = o.str("stream_icon"),
                categoryId = o.str("category_id"),
                num = o.optInt("num", 0),
            )
        }
        Kind.MOVIE -> o.optInt("stream_id", -1).takeIf { it >= 0 }?.let { id ->
            Entry(
                kind = kind, id = id,
                name = o.str("name") ?: "#$id",
                image = o.str("stream_icon"),
                categoryId = o.str("category_id"),
                ext = o.str("container_extension") ?: "mp4",
                rating = o.str("rating"),
            )
        }
        Kind.SERIES -> o.optInt("series_id", -1).takeIf { it >= 0 }?.let { id ->
            Entry(
                kind = kind, id = id,
                name = o.str("name") ?: "#$id",
                image = o.str("cover"),
                categoryId = o.str("category_id"),
                rating = o.str("rating"),
                plot = o.str("plot"),
            )
        }
    }

    private fun decodeBase64(s: String): String =
        runCatching { String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8).trim() }
            .getOrDefault(s)

    companion object {
        fun normalizeServer(raw: String): String {
            var s = raw.trim()
            if (!s.startsWith("http://", ignoreCase = true) && !s.startsWith("https://", ignoreCase = true)) {
                s = "http://$s"
            }
            val url = s.toHttpUrlOrNull() ?: return s.trimEnd('/')
            val path = url.encodedPath
                .substringBefore("/player_api.php")
                .substringBefore("/get.php")
                .trimEnd('/')
            val port = if (url.port == HttpUrl.defaultPort(url.scheme)) "" else ":${url.port}"
            return "${url.scheme}://${url.host}$port$path"
        }

        fun parseM3uLink(raw: String): Account? {
            val url = raw.trim().toHttpUrlOrNull() ?: return null
            val user = url.queryParameter("username")?.takeIf { it.isNotBlank() } ?: return null
            val pass = url.queryParameter("password")?.takeIf { it.isNotBlank() } ?: return null
            return Account(normalizeServer(raw), user, pass)
        }

        internal fun jsonList(body: String): List<JSONObject> {
            val t = body.trim()
            if (t.isEmpty() || t == "null") return emptyList()
            return when (t[0]) {
                '[' -> JSONArray(t).let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }
                '{' -> JSONObject(t).let { o -> o.keys().asSequence().mapNotNull { o.optJSONObject(it) }.toList() }
                else -> throw XtreamException(XtreamException.Reason.BAD_RESPONSE)
            }
        }

        internal fun JSONObject.str(key: String): String? {
            if (!has(key) || isNull(key)) return null
            return optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }
        }
    }
}
