package dev.zapette.player

data class PlayItem(
    val title: String,
    val url: String,
    val isLive: Boolean,
    val streamId: Int = 0,
    val resumeKey: String? = null,
)

object PlaybackQueue {
    var items: List<PlayItem> = emptyList()
        private set
    var startIndex: Int = 0

    fun set(items: List<PlayItem>, startIndex: Int) {
        this.items = items
        this.startIndex = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    }
}
