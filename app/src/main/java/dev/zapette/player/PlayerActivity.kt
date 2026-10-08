package dev.zapette.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.PlayerView
import dev.zapette.R
import dev.zapette.data.EpgItem
import dev.zapette.data.Http
import dev.zapette.data.Prefs
import dev.zapette.data.XtreamApi
import dev.zapette.ui.AppLanguage
import dev.zapette.ui.isTv
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var overlay: View
    private lateinit var overlayTitle: TextView
    private lateinit var overlayInfo: TextView
    private lateinit var overlayTech: TextView
    private lateinit var statusText: TextView
    private lateinit var prefs: Prefs

    private var api: XtreamApi? = null
    private var items: List<PlayItem> = emptyList()
    private val isLive get() = items.firstOrNull()?.isLive == true

    private var liveIndex = 0
    private var lastVodIndex = 0
    private var retries = 0
    private var stopped = false
    private var epgJob: Job? = null
    private val epgCache = HashMap<Int, Pair<Long, String>>()
    private val consumedKeys = HashSet<Int>()

    private val handler = Handler(Looper.getMainLooper())
    private val hideOverlayRunnable = Runnable { overlay.visibility = View.GONE }
    private val hideStatusRunnable = Runnable { statusText.visibility = View.GONE }
    private val pendingZap = Runnable { startLive() }
    private val reconnectRunnable = Runnable {
        if (isLive) player.seekToDefaultPosition()
        player.prepare()
        player.play()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        items = PlaybackQueue.items
        if (items.isEmpty()) {
            finish()
            return
        }
        val isTv = isTv()
        if (!isTv) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        setContentView(R.layout.activity_player)
        hideSystemBars()

        prefs = Prefs(this)
        Http.userAgent = prefs.userAgent
        api = prefs.account?.let { XtreamApi(it) }

        playerView = findViewById(R.id.player_view)
        overlay = findViewById(R.id.live_overlay)
        overlayTitle = findViewById(R.id.overlay_title)
        overlayInfo = findViewById(R.id.overlay_info)
        overlayTech = findViewById(R.id.overlay_tech)
        statusText = findViewById(R.id.status_text)

        player = buildPlayer()
        playerView.player = player
        playerView.keepScreenOn = true

        if (isLive) {
            playerView.useController = false
            liveIndex = PlaybackQueue.startIndex
            if (!isTv) setUpLiveTouch()
            startLive()
            showOverlay()
        } else {
            playerView.useController = true
            playerView.controllerShowTimeoutMs = 4000
            playerView.setShowSubtitleButton(true)
            startVod()
        }
        playerView.requestFocus()
    }

    private fun buildPlayer(): ExoPlayer {
        val dataSource = OkHttpDataSource.Factory(Http.client).setUserAgent(Http.userAgent)

        val extractors = DefaultExtractorsFactory()
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                    DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
            )
            .setConstantBitrateSeekingEnabled(true)

        val renderers = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(20_000, 60_000, 2_000, 4_000)
            .build()

        val audio = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        return ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource, extractors))
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .setAudioAttributes(audio, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { it.addListener(listener) }
    }

    private fun toMediaItem(item: PlayItem): MediaItem =
        MediaItem.Builder()
            .setUri(item.url)
            .setMediaId(item.resumeKey ?: item.url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).build())
            .apply { if (item.url.endsWith(".m3u8")) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()

    private fun startLive() {
        handler.removeCallbacks(reconnectRunnable)
        retries = 0
        hideStatus()
        player.setMediaItem(toMediaItem(items[liveIndex]))
        player.prepare()
        player.play()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setUpLiveTouch() {
        findViewById<View>(R.id.touch_controls).visibility = View.VISIBLE
        findViewById<View>(R.id.prev_channel).setOnClickListener { zap(-1) }
        findViewById<View>(R.id.next_channel).setOnClickListener { zap(+1) }

        val minSwipe = SWIPE_MIN_DP * resources.displayMetrics.density
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (overlay.visibility == View.VISIBLE) hideOverlay() else showOverlay()
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val start = e1 ?: return false
                val dx = e2.x - start.x
                val dy = e2.y - start.y
                if (abs(dy) < abs(dx) || abs(dy) < minSwipe) return false
                zap(if (dy < 0) +1 else -1)
                return true
            }
        })
        playerView.setOnTouchListener { _, event -> detector.onTouchEvent(event) }
    }

    private fun zap(delta: Int) {
        if (items.size < 2) {
            showOverlay()
            return
        }
        liveIndex = (liveIndex + delta).mod(items.size)
        PlaybackQueue.startIndex = liveIndex
        showOverlay()
        handler.removeCallbacks(pendingZap)
        handler.postDelayed(pendingZap, ZAP_DELAY_MS)
    }

    private fun showOverlay() {
        val item = items[liveIndex]
        overlayTitle.text = item.title
        overlayTech.text = techInfo()
        overlay.visibility = View.VISIBLE
        handler.removeCallbacks(hideOverlayRunnable)
        handler.postDelayed(hideOverlayRunnable, OVERLAY_MS)
        loadEpg(item)
    }

    private fun hideOverlay() {
        handler.removeCallbacks(hideOverlayRunnable)
        overlay.visibility = View.GONE
    }

    private fun loadEpg(item: PlayItem) {
        val cached = epgCache[item.streamId]
        if (cached != null && System.currentTimeMillis() - cached.first < EPG_CACHE_MS) {
            overlayInfo.text = cached.second
            return
        }
        epgJob?.cancel()
        val api = api ?: return
        overlayInfo.text = getString(R.string.player_epg_loading)
        epgJob = lifecycleScope.launch {
            val list = try {
                api.shortEpg(item.streamId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            val text = formatEpg(list)
            epgCache[item.streamId] = System.currentTimeMillis() to text
            if (items[liveIndex].streamId == item.streamId) overlayInfo.text = text
        }
    }

    private fun formatEpg(list: List<EpgItem>): String {
        if (list.isEmpty()) return getString(R.string.player_epg_none)
        val fmt = android.text.format.DateFormat.getTimeFormat(this)
        return list.take(2).mapIndexed { i, e ->
            val label = getString(if (i == 0) R.string.player_now else R.string.player_next)
            val hours = if (e.start > 0 && e.end > 0) {
                "${fmt.format(Date(e.start * 1000))}–${fmt.format(Date(e.end * 1000))}  "
            } else ""
            "$label · $hours${e.title}"
        }.joinToString("\n")
    }

    private fun startVod() {
        val start = PlaybackQueue.startIndex
        lastVodIndex = start
        val resumeMs = items[start].resumeKey?.let { prefs.position(it) } ?: 0L
        val startPos = if (resumeMs > RESUME_MIN_MS) resumeMs else 0L
        player.setMediaItems(items.map(::toMediaItem), start, startPos)
        player.prepare()
        player.play()
        showStatus(
            if (startPos > 0) {
                items[start].title + "\n" + getString(R.string.player_resume, formatTime(startPos))
            } else {
                items[start].title
            },
            3500,
        )
    }

    private fun seekBy(deltaMs: Long) {
        val duration = player.duration
        var target = (player.currentPosition + deltaMs).coerceAtLeast(0)
        if (duration != C.TIME_UNSET && duration > 0) target = target.coerceAtMost(duration - 1_000)
        player.seekTo(target)
        val total = if (duration != C.TIME_UNSET && duration > 0) " / ${formatTime(duration)}" else ""
        showStatus("${if (deltaMs < 0) "⏪" else "⏩"}  ${formatTime(target)}$total", 1500)
    }

    private fun saveProgress() {
        if (!::player.isInitialized || isLive) return
        val key = items.getOrNull(player.currentMediaItemIndex)?.resumeKey ?: return
        val duration = player.duration
        val position = player.currentPosition
        when {
            duration == C.TIME_UNSET || duration <= 0 ->
                if (position > RESUME_MIN_MS) prefs.savePosition(key, position)
            position > RESUME_MIN_MS && position < duration - END_MARGIN_MS ->
                prefs.savePosition(key, position)
            position >= duration - END_MARGIN_MS ->
                prefs.clearPosition(key)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (event.action == KeyEvent.ACTION_UP && consumedKeys.remove(code)) return true
        if (event.action == KeyEvent.ACTION_DOWN && ::player.isInitialized) {
            val handled = if (isLive) liveKey(code) else vodKey(code, event)
            if (handled) {
                consumedKeys += code
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun liveKey(code: Int): Boolean = when (code) {
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> {
            zap(+1); true
        }
        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
            zap(-1); true
        }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
            if (overlay.visibility == View.VISIBLE) hideOverlay() else showOverlay()
            true
        }
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_INFO -> {
            showOverlay(); true
        }
        KeyEvent.KEYCODE_BACK -> if (overlay.visibility == View.VISIBLE) {
            hideOverlay(); true
        } else false
        else -> false
    }

    private fun vodKey(code: Int, event: KeyEvent): Boolean {
        val controlsVisible = playerView.isControllerFullyVisible
        val step = when {
            event.repeatCount > 20 -> 60_000L
            event.repeatCount > 5 -> 30_000L
            else -> 10_000L
        }
        return when (code) {
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                seekBy(-step); true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                seekBy(step); true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> if (!controlsVisible) {
                seekBy(-step); true
            } else false
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (!controlsVisible) {
                seekBy(step); true
            } else false
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (player.isPlaying) player.pause() else player.play()
                showStatus(if (player.playWhenReady) "▶" else "⏸", 1000)
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                player.play(); true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                player.pause(); true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (player.hasNextMediaItem()) player.seekToNextMediaItem()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
                true
            }
            KeyEvent.KEYCODE_INFO -> {
                showStatus(techInfo().ifEmpty { getString(R.string.player_info_unavailable) }, 4000); true
            }
            KeyEvent.KEYCODE_BACK -> if (controlsVisible) {
                playerView.hideController(); true
            } else false
            else -> false
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    if (retries > 0) hideStatus()
                    retries = 0
                    if (overlay.visibility == View.VISIBLE) overlayTech.text = techInfo()
                }
                Player.STATE_ENDED -> if (isLive) {
                    reconnect(getString(R.string.player_stream_interrupted))
                } else {
                    items.getOrNull(player.currentMediaItemIndex)?.resumeKey?.let(prefs::clearPosition)
                    finish()
                }
                else -> Unit
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                player.seekToDefaultPosition()
                player.prepare()
                return
            }
            if (retries < MAX_RETRIES) {
                reconnect(getString(R.string.player_playback_problem))
            } else {
                showStatus(getString(R.string.player_failed, describe(error)), persistent = true)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (isLive) return
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                items.getOrNull(lastVodIndex)?.resumeKey?.let(prefs::clearPosition)
            }
            lastVodIndex = player.currentMediaItemIndex
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                items.getOrNull(lastVodIndex)?.let { showStatus(it.title, 3000) }
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            if (overlay.visibility == View.VISIBLE) overlayTech.text = techInfo()
        }
    }

    private fun reconnect(reason: String) {
        retries++
        showStatus(getString(R.string.player_reconnecting, reason, retries, MAX_RETRIES), persistent = true)
        handler.removeCallbacks(reconnectRunnable)
        handler.postDelayed(reconnectRunnable, 1_500L * retries)
    }

    private fun describe(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> getString(R.string.player_err_http)
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> getString(R.string.player_err_network)
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> getString(R.string.player_err_decoder)
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> getString(R.string.player_err_container)
        else -> error.errorCodeName
    }

    private fun techInfo(): String {
        val parts = mutableListOf<String>()
        player.videoFormat?.let { f ->
            if (f.width > 0 && f.height > 0) parts += "${f.width}×${f.height}"
            (f.codecs ?: f.sampleMimeType?.substringAfter('/'))?.let { parts += it }
            if (f.frameRate > 0) parts += getString(R.string.player_fps, String.format(Locale.getDefault(), "%.0f", f.frameRate))
            val transfer = f.colorInfo?.colorTransfer
            if (transfer == C.COLOR_TRANSFER_ST2084 || transfer == C.COLOR_TRANSFER_HLG) parts += "HDR"
        }
        player.audioFormat?.let { a ->
            a.sampleMimeType?.substringAfter('/')?.let { parts += it }
            if (a.channelCount > 0) parts += getString(R.string.player_audio_channels, a.channelCount)
        }
        return parts.joinToString(" · ")
    }

    private fun showStatus(text: String, durationMs: Long = 2500, persistent: Boolean = false) {
        statusText.text = text
        statusText.visibility = View.VISIBLE
        handler.removeCallbacks(hideStatusRunnable)
        if (!persistent) handler.postDelayed(hideStatusRunnable, durationMs)
    }

    private fun hideStatus() {
        handler.removeCallbacks(hideStatusRunnable)
        statusText.visibility = View.GONE
    }

    private fun formatTime(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onStart() {
        super.onStart()
        if (stopped && ::player.isInitialized) {
            stopped = false
            if (isLive) player.seekToDefaultPosition()
            player.prepare()
            player.play()
        }
    }

    override fun onStop() {
        if (::player.isInitialized) {
            saveProgress()
            stopped = true
            if (isLive) player.stop() else player.pause()
        }
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::player.isInitialized) {
            player.removeListener(listener)
            player.release()
        }
        super.onDestroy()
    }

    private companion object {
        const val MAX_RETRIES = 5
        const val ZAP_DELAY_MS = 450L
        const val OVERLAY_MS = 6_000L
        const val EPG_CACHE_MS = 120_000L
        const val RESUME_MIN_MS = 30_000L
        const val END_MARGIN_MS = 120_000L
        const val SWIPE_MIN_DP = 60
    }
}
