package dev.zapette.data

interface Catalog {
    val account: Account
    val kinds: List<Kind>

    suspend fun verify(): Account
    suspend fun accountInfo(): AccountInfo?
    suspend fun categories(kind: Kind): List<Category>
    suspend fun streams(kind: Kind, categoryId: String?): List<Entry>
    suspend fun seriesInfo(seriesId: Int): SeriesDetail
    suspend fun shortEpg(streamId: Int, limit: Int = 3): List<EpgItem>

    fun liveUrl(entry: Entry, format: LiveFormat): String
    fun movieUrl(entry: Entry): String
    fun episodeUrl(episode: Episode): String

    companion object {
        fun create(account: Account, cache: CatalogCache? = null): Catalog =
            if (account.isPlaylist) M3uPlaylist(account, cache) else XtreamApi(account, cache)
    }
}
