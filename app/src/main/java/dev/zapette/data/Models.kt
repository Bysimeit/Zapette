package dev.zapette.data

import androidx.annotation.StringRes
import dev.zapette.R

enum class Kind(@StringRes val label: Int) {
    LIVE(R.string.tab_live),
    MOVIE(R.string.tab_movies),
    SERIES(R.string.tab_series),
}

data class Account(
    val server: String,
    val username: String,
    val password: String,
) {
    val isPlaylist: Boolean get() = username.isBlank() && password.isBlank()
}

enum class LiveFormat(val ext: String, val label: String) {
    TS("ts", "MPEG-TS (.ts)"),
    HLS("m3u8", "HLS (.m3u8)"),
}

data class Category(
    val id: String,
    val name: String,
)

data class Entry(
    val kind: Kind,
    val id: Int,
    val name: String,
    val image: String?,
    val categoryId: String?,
    val num: Int = 0,
    val ext: String? = null,
    val rating: String? = null,
    val plot: String? = null,
    val url: String? = null,
    val userAgent: String? = null,
)

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val number: Int,
    val ext: String,
    val image: String?,
    val plot: String?,
    val duration: String?,
)

data class SeriesDetail(
    val name: String,
    val plot: String?,
    val cover: String?,
    val genre: String?,
    val releaseDate: String?,
    val seasons: Map<Int, List<Episode>>,
)

data class AccountInfo(
    val status: String,
    val expiresAt: Long?,
    val maxConnections: String?,
    val activeConnections: String?,
    val isTrial: Boolean,
)

data class EpgItem(
    val title: String,
    val start: Long,
    val end: Long,
)
