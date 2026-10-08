package dev.zapette.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class M3uPlaylist(override val account: Account, private val cache: CatalogCache? = null) : Catalog {

    private val url = account.server.trim()
    private val mutex = Mutex()
    private var channels: List<PlaylistChannel>? = null

    override val kinds = listOf(Kind.LIVE)

    override suspend fun verify(): Account {
        load()
        return account.copy(server = url)
    }

    override suspend fun accountInfo(): AccountInfo? = null

    override suspend fun categories(kind: Kind): List<Category> {
        if (kind != Kind.LIVE) return emptyList()
        return load()
            .flatMap { it.groups }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .map { Category(it, it) }
    }

    override suspend fun streams(kind: Kind, categoryId: String?): List<Entry> {
        if (kind != Kind.LIVE) return emptyList()
        val all = load()
        return if (categoryId == null) all.map { it.entry } else all.filter { categoryId in it.groups }.map { it.entry }
    }

    override suspend fun seriesInfo(seriesId: Int): SeriesDetail =
        throw XtreamException(XtreamException.Reason.BAD_RESPONSE)

    override suspend fun shortEpg(streamId: Int, limit: Int): List<EpgItem> = emptyList()

    override fun liveUrl(entry: Entry, format: LiveFormat): String = entry.url.orEmpty()

    override fun movieUrl(entry: Entry): String = entry.url.orEmpty()

    override fun episodeUrl(episode: Episode): String = ""

    private suspend fun load(): List<PlaylistChannel> {
        channels?.let { return it }
        return mutex.withLock {
            channels ?: withContext(Dispatchers.IO) {
                val httpUrl = url.toHttpUrlOrNull() ?: throw XtreamException(XtreamException.Reason.INVALID_URL)
                val cached = cache?.read(url)?.let { runCatching { parse(it) }.getOrNull() }
                cached ?: Http.fetch(httpUrl).let { body ->
                    parse(body).also { runCatching { cache?.write(url, body) } }
                }
            }.also { channels = it }
        }
    }

    data class PlaylistChannel(val entry: Entry, val groups: List<String>)

    companion object {
        private val ATTRIBUTE = Regex("""([\w-]+)="([^"]*)"""")

        fun parse(text: String): List<PlaylistChannel> {
            if (!text.contains("#EXTINF")) throw XtreamException(XtreamException.Reason.NOT_PLAYLIST)
            val result = mutableListOf<PlaylistChannel>()
            var name: String? = null
            var logo: String? = null
            var groups: List<String> = emptyList()
            var userAgent: String? = null

            text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
                when {
                    line.startsWith("#EXTINF", ignoreCase = true) -> {
                        val matches = ATTRIBUTE.findAll(line).toList()
                        val attributes = matches.associate { it.groupValues[1].lowercase() to it.groupValues[2].trim() }
                        val nameStart = line.indexOf(',', matches.lastOrNull()?.range?.last ?: 0)
                        name = if (nameStart >= 0) line.substring(nameStart + 1).trim() else null
                        logo = attributes["tvg-logo"]?.takeIf { it.isNotEmpty() }
                        groups = attributes["group-title"].orEmpty().split(';').map { it.trim() }.filter { it.isNotEmpty() }
                        userAgent = attributes["http-user-agent"]?.takeIf { it.isNotEmpty() }
                    }
                    line.startsWith("#EXTGRP:", ignoreCase = true) -> if (groups.isEmpty()) {
                        groups = listOf(line.substringAfter(':').trim()).filter { it.isNotEmpty() }
                    }
                    line.startsWith("#EXTVLCOPT:http-user-agent=", ignoreCase = true) ->
                        userAgent = line.substringAfter('=').trim().takeIf { it.isNotEmpty() }
                    line.startsWith("#") -> Unit
                    else -> {
                        val entry = Entry(
                            kind = Kind.LIVE,
                            id = line.hashCode(),
                            name = name?.takeIf { it.isNotEmpty() } ?: line,
                            image = logo,
                            categoryId = groups.firstOrNull(),
                            url = line,
                            userAgent = userAgent,
                        )
                        result += PlaylistChannel(entry, groups)
                        name = null
                        logo = null
                        groups = emptyList()
                        userAgent = null
                    }
                }
            }
            return result
        }
    }
}
