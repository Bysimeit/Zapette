package dev.zapette.ui

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import dev.zapette.R
import dev.zapette.data.XtreamException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.zapette.data.Account
import dev.zapette.data.AccountInfo
import dev.zapette.data.Category
import dev.zapette.data.Entry
import dev.zapette.data.Episode
import dev.zapette.data.Http
import dev.zapette.data.Kind
import dev.zapette.data.LiveFormat
import dev.zapette.data.Prefs
import dev.zapette.data.SeriesDetail
import dev.zapette.data.XtreamApi
import dev.zapette.player.PlayItem
import dev.zapette.player.PlaybackQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.Normalizer

sealed interface Load<out T> {
    data object Idle : Load<Nothing>
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Err(val message: String) : Load<Nothing>
}

enum class Tab(@StringRes val label: Int, val kind: Kind?) {
    LIVE(R.string.tab_live, Kind.LIVE),
    MOVIE(R.string.tab_movies, Kind.MOVIE),
    SERIES(R.string.tab_series, Kind.SERIES),
    SEARCH(R.string.tab_search, null),
    ACCOUNT(R.string.tab_account, null),
}

class KindState {
    var categories by mutableStateOf<Load<List<Category>>>(Load.Idle)
    var selected by mutableStateOf<String?>(null)
    var items by mutableStateOf<Load<List<Entry>>>(Load.Idle)

    fun reset() {
        categories = Load.Idle
        selected = null
        items = Load.Idle
    }
}

const val CAT_FAVORITES = "__fav"
const val CAT_ALL = "__all"

class BrowseViewModel(app: Application) : AndroidViewModel(app) {

    val prefs = Prefs(app)

    var api by mutableStateOf(prefs.account?.let { XtreamApi(it) })
        private set

    var tab by mutableStateOf(Tab.LIVE)

    private val states = Kind.entries.associateWith { KindState() }
    fun state(kind: Kind): KindState = states.getValue(kind)

    private val streamCache = HashMap<String, List<Entry>>()
    private val allCache = HashMap<Kind, List<Entry>>()
    private val allMutex = Mutex()
    private val itemJobs = HashMap<Kind, Job>()

    var favorites by mutableStateOf(Kind.entries.associateWith { prefs.favorites(it) })
        private set

    var seriesDetail by mutableStateOf<Load<SeriesDetail>>(Load.Idle)
        private set
    var seriesLoadedId: Int? = null
        private set
    private var seriesJob: Job? = null

    var accountInfo by mutableStateOf<Load<AccountInfo>>(Load.Idle)
        private set

    var searchQuery by mutableStateOf("")
        private set
    var searchKind by mutableStateOf(Kind.LIVE)
        private set
    var searchResults by mutableStateOf<Load<List<Entry>>>(Load.Idle)
        private set
    private var searchJob: Job? = null

    var liveFormat by mutableStateOf(prefs.liveFormat)
        private set
    var userAgent by mutableStateOf(prefs.userAgent)
        private set

    var resumeTick by mutableIntStateOf(0)
        private set

    var focusGridOnReturn = false

    init {
        Http.userAgent = prefs.userAgent
    }

    suspend fun login(account: Account): Result<Unit> {
        return try {
            val candidate = XtreamApi(account)
            candidate.accountInfo()
            val saved = account.copy(server = candidate.base)
            prefs.account = saved
            resetData()
            api = XtreamApi(saved)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(e.userMessage(getApplication<Application>()), e))
        }
    }

    fun logout() {
        prefs.account = null
        api = null
        resetData()
    }

    fun loadAccountInfo() {
        val api = api ?: return
        accountInfo = Load.Loading
        viewModelScope.launch { accountInfo = attempt { api.accountInfo() } }
    }

    fun updateLiveFormat(format: LiveFormat) {
        prefs.liveFormat = format
        liveFormat = format
    }

    fun updateUserAgent(value: String) {
        userAgent = value
        prefs.userAgent = value
        Http.userAgent = prefs.userAgent
    }

    fun clearCache() {
        streamCache.clear()
        allCache.clear()
        states.values.forEach { it.reset() }
        seriesLoadedId = null
        searchResults = Load.Idle
    }

    private fun resetData() {
        clearCache()
        seriesDetail = Load.Idle
        accountInfo = Load.Idle
        searchQuery = ""
        tab = Tab.LIVE
    }

    fun onResumed() {
        resumeTick++
    }

    fun ensureCategories(kind: Kind) {
        val st = state(kind)
        if (st.categories is Load.Ok || st.categories is Load.Loading) return
        val api = api ?: return
        st.categories = Load.Loading
        viewModelScope.launch {
            val result = attempt { api.categories(kind) }
            st.categories = result
            if (result is Load.Ok && st.selected == null) {
                val first = when {
                    favorites[kind].orEmpty().isNotEmpty() -> CAT_FAVORITES
                    else -> result.value.firstOrNull()?.id ?: CAT_ALL
                }
                selectCategory(kind, first)
            }
        }
    }

    fun retryCategories(kind: Kind) {
        state(kind).categories = Load.Idle
        ensureCategories(kind)
    }

    fun selectCategory(kind: Kind, categoryId: String, force: Boolean = false) {
        val st = state(kind)
        if (!force && st.selected == categoryId && (st.items is Load.Ok || st.items is Load.Loading)) return
        val api = api ?: return
        st.selected = categoryId
        itemJobs[kind]?.cancel()
        st.items = Load.Loading
        itemJobs[kind] = viewModelScope.launch {
            st.items = attempt {
                when (categoryId) {
                    CAT_FAVORITES -> {
                        val favs = favorites[kind].orEmpty()
                        loadAll(kind).filter { it.id.toString() in favs }
                    }
                    CAT_ALL -> loadAll(kind)
                    else -> streamCache.getOrPut("${kind.name}/$categoryId") {
                        api.streams(kind, categoryId).distinctBy { it.id }
                    }
                }
            }
        }
    }

    private suspend fun loadAll(kind: Kind): List<Entry> {
        allCache[kind]?.let { return it }
        val api = api ?: error("Not signed in")
        return allMutex.withLock {
            allCache[kind] ?: api.streams(kind, null).distinctBy { it.id }.also { allCache[kind] = it }
        }
    }

    fun isFavorite(entry: Entry): Boolean = entry.id.toString() in favorites[entry.kind].orEmpty()

    fun toggleFavorite(entry: Entry): Boolean {
        val added = prefs.toggleFavorite(entry.kind, entry.id.toString())
        favorites = favorites + (entry.kind to prefs.favorites(entry.kind))
        if (state(entry.kind).selected == CAT_FAVORITES) selectCategory(entry.kind, CAT_FAVORITES, force = true)
        return added
    }

    fun loadSeries(entry: Entry, force: Boolean = false) {
        if (!force && seriesLoadedId == entry.id && seriesDetail is Load.Ok) return
        val api = api ?: return
        seriesJob?.cancel()
        seriesLoadedId = entry.id
        seriesDetail = Load.Loading
        seriesJob = viewModelScope.launch { seriesDetail = attempt { api.seriesInfo(entry.id) } }
    }

    fun onSearchChange(query: String) {
        searchQuery = query
        runSearch()
    }

    fun updateSearchKind(kind: Kind) {
        searchKind = kind
        runSearch()
    }

    private fun runSearch() {
        searchJob?.cancel()
        val query = normalize(searchQuery.trim())
        val kind = searchKind
        if (query.length < 2) {
            searchResults = Load.Idle
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            searchResults = Load.Loading
            searchResults = attempt {
                val all = loadAll(kind)
                withContext(Dispatchers.Default) {
                    all.filter { normalize(it.name).contains(query) }.take(MAX_SEARCH_RESULTS)
                }
            }
        }
    }

    fun playLive(list: List<Entry>, index: Int) {
        val api = api ?: return
        val format = liveFormat
        PlaybackQueue.set(
            list.map {
                PlayItem(
                    title = if (it.num > 0) "${it.num}  ${it.name}" else it.name,
                    url = api.liveUrl(it.id, format),
                    isLive = true,
                    streamId = it.id,
                )
            },
            index,
        )
    }

    fun playMovie(entry: Entry) {
        val api = api ?: return
        PlaybackQueue.set(
            listOf(
                PlayItem(
                    title = entry.name,
                    url = api.movieUrl(entry.id, entry.ext ?: "mp4"),
                    isLive = false,
                    streamId = entry.id,
                    resumeKey = movieKey(entry.id),
                )
            ),
            0,
        )
    }

    fun playEpisodes(episodes: List<Episode>, index: Int) {
        val api = api ?: return
        PlaybackQueue.set(
            episodes.map { ep ->
                PlayItem(
                    title = "S${ep.season} E${ep.number} · ${ep.title}",
                    url = api.episodeUrl(ep.id, ep.ext),
                    isLive = false,
                    resumeKey = episodeKey(ep.id),
                )
            },
            index,
        )
    }

    fun resumePosition(key: String): Long = prefs.position(key)

    private suspend fun <T> attempt(block: suspend () -> T): Load<T> = try {
        Load.Ok(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Load.Err(e.userMessage(getApplication<Application>()))
    }

    companion object {
        private const val MAX_SEARCH_RESULTS = 500
        private val DIACRITICS = Regex("\\p{Mn}+")

        fun movieKey(id: Int) = "movie_$id"
        fun episodeKey(id: String) = "ep_$id"

        fun normalize(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase()
    }
}

fun Throwable.userMessage(context: Context): String = when (this) {
    is XtreamException -> when (reason) {
        XtreamException.Reason.NOT_XTREAM -> context.getString(R.string.error_not_xtream)
        XtreamException.Reason.AUTH_REJECTED -> context.getString(R.string.error_auth)
        XtreamException.Reason.INVALID_URL -> context.getString(R.string.error_invalid_url)
        XtreamException.Reason.BAD_RESPONSE -> context.getString(R.string.error_bad_response)
        XtreamException.Reason.HTTP -> when (httpCode) {
            401, 403 -> context.getString(R.string.error_http_denied, httpCode)
            404 -> context.getString(R.string.error_http_404)
            else -> context.getString(R.string.error_http_other, httpCode)
        }
    }
    is UnknownHostException -> context.getString(R.string.error_unknown_host)
    is SocketTimeoutException -> context.getString(R.string.error_timeout)
    is ConnectException -> context.getString(R.string.error_connect)
    is JSONException -> context.getString(R.string.error_bad_response)
    else -> message ?: javaClass.simpleName
}
