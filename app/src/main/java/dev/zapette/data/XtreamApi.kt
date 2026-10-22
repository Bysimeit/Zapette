package dev.zapette.data

import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.Reader

class XtreamException(val reason: Reason, val httpCode: Int = 0) : IOException(reason.name) {
    enum class Reason { NOT_XTREAM, NOT_PLAYLIST, AUTH_REJECTED, INVALID_URL, HTTP, BAD_RESPONSE }
}

class XtreamApi(override val account: Account, private val cache: CatalogCache? = null) : Catalog {

    val base: String = normalizeServer(account.server)

    override val kinds = Kind.entries

    override suspend fun verify(): Account {
        accountInfo()
        return account.copy(server = base)
    }

    override suspend fun accountInfo(): AccountInfo = call(apiUrl(null)) { body ->
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

    override suspend fun categories(kind: Kind): List<Category> {
        val action = when (kind) {
            Kind.LIVE -> "get_live_categories"
            Kind.MOVIE -> "get_vod_categories"
            Kind.SERIES -> "get_series_categories"
        }
        return call(apiUrl(action), cached = true) { body ->
            jsonList(body).mapNotNull { o ->
                val id = o.str("category_id") ?: return@mapNotNull null
                Category(id, o.str("category_name") ?: "#$id")
            }
        }
    }

    override suspend fun streams(kind: Kind, categoryId: String?): List<Entry> {
        val action = when (kind) {
            Kind.LIVE -> "get_live_streams"
            Kind.MOVIE -> "get_vod_streams"
            Kind.SERIES -> "get_series"
        }
        val params = if (categoryId != null) arrayOf("category_id" to categoryId) else emptyArray()
        return callStreamed(apiUrl(action, *params)) { readEntries(kind, it) }
    }

    override suspend fun seriesInfo(seriesId: Int): SeriesDetail =
        call(apiUrl("get_series_info", "series_id" to seriesId.toString()), cached = true) { body ->
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

    override suspend fun shortEpg(streamId: Int, limit: Int): List<EpgItem> =
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

    override suspend fun movieInfo(movieId: Int): MovieDetail =
        call(apiUrl("get_vod_info", "vod_id" to movieId.toString()), cached = true) { parseMovieDetail(it) }

    override fun liveUrl(entry: Entry, format: LiveFormat): String = streamUrl("live", "${entry.id}.${format.ext}")

    override fun movieUrl(entry: Entry): String = streamUrl("movie", "${entry.id}.${entry.ext ?: "mp4"}")

    override fun episodeUrl(episode: Episode): String = streamUrl("series", "${episode.id}.${episode.ext}")

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

    private suspend fun <T> call(
        url: HttpUrl,
        cached: Boolean = false,
        parse: (String) -> T,
    ): T = withContext(Dispatchers.IO) {
        val store = cache.takeIf { cached }
        val key = url.toString()
        store?.read(key)?.let { body ->
            runCatching { parse(body) }.onSuccess { return@withContext it }
        }
        val body = Http.fetch(url)
        parse(body).also { runCatching { store?.write(key, body) } }
    }

    private suspend fun <T> callStreamed(url: HttpUrl, parse: (Reader) -> T): T = withContext(Dispatchers.IO) {
        val key = url.toString()
        cache?.file(key)?.let { file ->
            runCatching { file.bufferedReader().use(parse) }.onSuccess { return@withContext it }
        }
        val tmp = File.createTempFile("catalog", ".json")
        try {
            Http.download(url, tmp)
            tmp.bufferedReader().use(parse).also { runCatching { cache?.adopt(key, tmp) } }
        } finally {
            tmp.delete()
        }
    }

    private fun readEntries(kind: Kind, source: Reader): List<Entry> {
        val reader = JsonReader(source).apply { isLenient = true }
        val entries = ArrayList<Entry>()
        try {
            when (reader.peek()) {
                JsonToken.BEGIN_ARRAY -> {
                    reader.beginArray()
                    while (reader.hasNext()) readEntry(kind, reader)?.let(entries::add)
                    reader.endArray()
                }
                JsonToken.BEGIN_OBJECT -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        reader.nextName()
                        readEntry(kind, reader)?.let(entries::add)
                    }
                    reader.endObject()
                }
                JsonToken.NULL, JsonToken.END_DOCUMENT -> Unit
                else -> throw XtreamException(XtreamException.Reason.BAD_RESPONSE)
            }
        } catch (e: EOFException) {
            if (entries.isNotEmpty()) throw XtreamException(XtreamException.Reason.BAD_RESPONSE)
        } catch (e: XtreamException) {
            throw e
        } catch (e: IOException) {
            throw XtreamException(XtreamException.Reason.BAD_RESPONSE)
        } catch (e: IllegalStateException) {
            throw XtreamException(XtreamException.Reason.BAD_RESPONSE)
        }
        return entries
    }

    private fun readEntry(kind: Kind, reader: JsonReader): Entry? {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }
        val fields = HashMap<String, String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            if (name in ENTRY_FIELDS && reader.peek().let { it == JsonToken.STRING || it == JsonToken.NUMBER }) {
                fields[name] = reader.nextString()
            } else {
                reader.skipValue()
            }
        }
        reader.endObject()
        return parseEntry(kind) { key -> fields[key]?.trim()?.takeIf { it.isNotEmpty() && it != "null" } }
    }

    private fun parseEntry(kind: Kind, str: (String) -> String?): Entry? {
        fun int(key: String) = str(key)?.let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() }
        return when (kind) {
            Kind.LIVE -> int("stream_id")?.takeIf { it >= 0 }?.let { id ->
                Entry(
                    kind = kind, id = id,
                    name = str("name") ?: "#$id",
                    image = str("stream_icon"),
                    categoryId = str("category_id"),
                    num = int("num") ?: 0,
                )
            }
            Kind.MOVIE -> int("stream_id")?.takeIf { it >= 0 }?.let { id ->
                Entry(
                    kind = kind, id = id,
                    name = str("name") ?: "#$id",
                    image = str("stream_icon"),
                    categoryId = str("category_id"),
                    ext = str("container_extension") ?: "mp4",
                    rating = str("rating"),
                )
            }
            Kind.SERIES -> int("series_id")?.takeIf { it >= 0 }?.let { id ->
                Entry(
                    kind = kind, id = id,
                    name = str("name") ?: "#$id",
                    image = str("cover"),
                    categoryId = str("category_id"),
                    rating = str("rating"),
                    plot = str("plot"),
                )
            }
        }
    }

    private fun decodeBase64(s: String): String =
        runCatching { String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8).trim() }
            .getOrDefault(s)

    companion object {
        private val ENTRY_FIELDS = setOf(
            "stream_id", "series_id", "name", "stream_icon", "cover", "category_id",
            "num", "container_extension", "rating", "plot",
        )

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

        internal fun parseMovieDetail(body: String): MovieDetail {
            val info = JSONObject(body).optJSONObject("info") ?: JSONObject()
            return MovieDetail(
                plot = info.str("plot") ?: info.str("description"),
                cover = info.str("movie_image") ?: info.str("cover_big"),
                genre = info.str("genre"),
                releaseDate = info.str("releasedate") ?: info.str("release_date"),
                minutes = runtimeMinutes(info),
                director = info.str("director"),
                cast = info.str("cast") ?: info.str("actors"),
            )
        }

        private fun runtimeMinutes(info: JSONObject): Int? {
            info.str("duration_secs")?.toIntOrNull()?.takeIf { it > 0 }?.let { return (it + 30) / 60 }
            info.str("duration")?.let { d ->
                val parts = d.split(':').map { it.trim().toIntOrNull() ?: return@let }
                if (parts.size == 3) return (parts[0] * 60 + parts[1]).takeIf { it > 0 }
            }
            return (info.str("episode_run_time") ?: info.str("runtime"))?.toIntOrNull()?.takeIf { it > 0 }
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
